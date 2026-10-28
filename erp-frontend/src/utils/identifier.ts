/** API identifiers are strings; safe numeric literals remain accepted for legacy clients. */
export type EntityId = string | number

export function idKey(value: EntityId | null | undefined): string {
  return value == null ? '' : String(value)
}

export function sameId(left: unknown, right: unknown): boolean {
  return left != null && right != null && String(left) === String(right)
}
