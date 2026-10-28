import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

function load(relativePath, exports, mocks = {}) {
  const source = readFileSync(new URL('../src/' + relativePath, import.meta.url), 'utf8')
  const script = relativePath.endsWith('.vue')
    ? source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1] : source
  const js = ts.transpileModule(script + '\nexport { ' + exports.join(', ') + ' };', {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const value = {}
  const vue = { ref: v => ({ value: v }), reactive: v => v, onMounted() {}, nextTick: async () => {},
    computed: fn => ({ get value() { return fn() } }) }
  new Function('require', 'exports', js)(name => {
    if (name in mocks) return mocks[name]
    if (name === 'vue') return vue
    if (name === '@/utils/identifier') return identifiers
    if (name === 'element-plus' || name === '@/api/system') return {}
    throw new Error('Unexpected import: ' + name)
  }, value)
  return value
}

const identifiers = load('utils/identifier.ts', [])
const first = '2112086874144161794'
const second = '2112086874144161795'

test('adjacent 19-digit IDs remain distinct after browser JSON parsing and comparison', () => {
  assert.equal(Number(first), Number(second)) // Demonstrates the old unsafe representation.
  const rows = JSON.parse(JSON.stringify([{ id: first }, { id: second }]))
  assert.equal(identifiers.sameId(rows[0].id, rows[1].id), false)
  assert.equal(identifiers.idKey(rows[0].id), first)
  assert.equal(identifiers.sameId('0', 0), true)
  assert.equal(identifiers.sameId('1', 1), true)
  assert.equal(identifiers.sameId(undefined, undefined), false)
})

test('role tree and leaf permissions match string IDs and legacy numeric roots', () => {
  const { buildTree, leafCheckedIds, isBuiltinAdminRole } = load('views/system/role/index.vue',
    ['buildTree', 'leafCheckedIds', 'isBuiltinAdminRole'])
  const permissions = [{ id: first, parentId: 0 }, { id: second, parentId: first }]
  const roots = buildTree(permissions)
  assert.equal(roots.length, 1)
  assert.equal(roots[0].children[0].id, second)
  assert.deepEqual(leafCheckedIds(permissions, [first, second]), [second])
  assert.equal(isBuiltinAdminRole({ id: '0' }), true)
  assert.equal(isBuiltinAdminRole({ id: '1' }), true)
  assert.equal(isBuiltinAdminRole({ id: 1 }), true)
  assert.equal(isBuiltinAdminRole({ id: first }), false)
})

test('permission editor builds mixed-root trees without treating IDs as numbers', () => {
  const { buildTree, parentOptions } = load('views/system/permission/index.vue', ['buildTree', 'parentOptions'])
  const roots = buildTree([{ id: 1, parentId: '0' }, { id: first, parentId: '1' }, { id: second, parentId: first }])
  assert.equal(roots.length, 1)
  assert.equal(roots[0].children[0].children[0].id, second)
  assert.equal(parentOptions.value[0].id, '0')
})

test('editing the built-in user keeps its string super-admin role selected', () => {
  const { openEdit, form } = load('views/system/user/index.vue', ['openEdit', 'form'], {
    '@/stores/user': { useUserStore: () => ({ userId: '1' }) }
  })
  openEdit({ id: '1', username: 'admin', roleIds: ['0'] })
  assert.deepEqual(form.roleIds, ['0'])
})

test('shipment updates keep the exact ID and omit display-only PI/SI references', async () => {
  let submitted
  const { updateShipment } = load('api/shipment.ts', [], {
    './request': { default: { put: async (url, data) => { submitted = { url, data } } } }
  })
  await updateShipment({ id: first, piId: second, siId: second, shipmentNo: 'SHP-DEMO',
    status: 'FULL_SHIPPED', qty: 10, packageCount: 2 })
  assert.equal(submitted.data.id, first)
  assert.equal(submitted.data.qty, 10)
  for (const key of ['piId', 'siId', 'shipmentNo', 'status']) {
    assert.equal(key in submitted.data, false, key)
  }
})

test('user and permission API calls preserve long IDs in URLs and request arrays', async () => {
  const calls = []
  const { rolePermissionIds, assignRolePermissions, resetPassword } = load('api/system.ts', [], {
    './request': { default: { get: async (...args) => calls.push(args), put: async (...args) => calls.push(args) } }
  })
  await rolePermissionIds(first)
  await assignRolePermissions(first, [first, second])
  await resetPassword(second, 'demo-placeholder')
  assert.equal(calls[0][0], `/system/role/${first}/permissions`)
  assert.deepEqual(calls[1][1].permissionIds, [first, second])
  assert.equal(calls[2][1].userId, second)
})
