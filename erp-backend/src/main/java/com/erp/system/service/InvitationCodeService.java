package com.erp.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.system.dto.InvitationCodeCreateDTO;
import com.erp.system.dto.InvitationCodeVO;
import com.erp.system.entity.InvitationCode;
import com.erp.system.mapper.InvitationCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

public interface InvitationCodeService extends IService<InvitationCode> {

    IPage<InvitationCodeVO> pageVO(PageQuery query);

    InvitationCodeVO createCode(InvitationCodeCreateDTO dto);

    void deleteCode(Long id);

    void consume(String code);

    @Service
    class Impl extends ServiceImpl<InvitationCodeMapper, InvitationCode> implements InvitationCodeService {

        private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
        private static final SecureRandom RANDOM = new SecureRandom();

        @Override
        public IPage<InvitationCodeVO> pageVO(PageQuery query) {
            Page<InvitationCode> page = page(
                    new Page<>(query.getPageNum(), query.getPageSize()),
                    Wrappers.<InvitationCode>lambdaQuery().orderByDesc(InvitationCode::getCreateTime));
            return page.convert(this::toVO);
        }

        @Override
        public InvitationCodeVO createCode(InvitationCodeCreateDTO dto) {
            if (dto.getExpireTime() != null && !dto.getExpireTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException("邀请码有效期必须晚于当前时间");
            }
            InvitationCode code = new InvitationCode();
            code.setInviteCode(generateUniqueCode());
            code.setExpireTime(dto.getExpireTime());
            code.setMaxUses(dto.getMaxUses());
            code.setUsedCount(0);
            save(code);
            return toVO(code);
        }

        @Override
        public void deleteCode(Long id) {
            if (id == null || getById(id) == null) {
                throw new BusinessException("邀请码不存在");
            }
            removeById(id);
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void consume(String input) {
            String normalized = normalize(input);
            if (!StringUtils.hasText(normalized)) {
                throw new BusinessException("请输入邀请码");
            }
            InvitationCode code = getOne(Wrappers.<InvitationCode>lambdaQuery()
                    .eq(InvitationCode::getInviteCode, normalized));
            if (code == null) {
                throw new BusinessException("邀请码不正确");
            }
            if (isExpired(code.getExpireTime(), LocalDateTime.now())) {
                throw new BusinessException("邀请码已过期");
            }
            if (code.getUsedCount() >= code.getMaxUses()) {
                throw new BusinessException("邀请码使用次数已用完");
            }
            if (baseMapper.consume(code.getId()) != 1) {
                throw new BusinessException("邀请码已失效, 请联系管理员");
            }
        }

        private String generateUniqueCode() {
            for (int attempt = 0; attempt < 10; attempt++) {
                String candidate = generateCode();
                if (count(Wrappers.<InvitationCode>lambdaQuery()
                        .eq(InvitationCode::getInviteCode, candidate)) == 0) {
                    return candidate;
                }
            }
            throw new BusinessException("邀请码生成失败, 请稍后重试");
        }

        static String generateCode() {
            StringBuilder value = new StringBuilder("XF");
            for (int group = 0; group < 3; group++) {
                value.append('-');
                for (int i = 0; i < 4; i++) {
                    value.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
                }
            }
            return value.toString();
        }

        private String normalize(String code) {
            return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : "";
        }

        private InvitationCodeVO toVO(InvitationCode code) {
            InvitationCodeVO vo = new InvitationCodeVO();
            vo.setId(code.getId());
            vo.setInviteCode(code.getInviteCode());
            vo.setExpireTime(code.getExpireTime());
            vo.setMaxUses(code.getMaxUses());
            vo.setUsedCount(code.getUsedCount());
            vo.setRemainingUses(Math.max(0, code.getMaxUses() - code.getUsedCount()));
            if (isExpired(code.getExpireTime(), LocalDateTime.now())) {
                vo.setStatus("EXPIRED");
            } else if (code.getUsedCount() >= code.getMaxUses()) {
                vo.setStatus("EXHAUSTED");
            } else {
                vo.setStatus("VALID");
            }
            vo.setCreateTime(code.getCreateTime());
            return vo;
        }

        static boolean isExpired(LocalDateTime expireTime, LocalDateTime now) {
            return expireTime != null && !expireTime.isAfter(now);
        }
    }
}
