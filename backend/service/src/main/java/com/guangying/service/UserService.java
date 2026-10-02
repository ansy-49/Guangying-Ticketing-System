package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.common.constants.CommonConstants;
import com.guangying.common.utils.JwtUtil;
import com.guangying.common.utils.PasswordUtil;
import com.guangying.dao.mapper.UserMapper;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.model.dto.UserLoginDTO;
import com.guangying.domain.model.dto.UserRegisterDTO;
import com.guangying.domain.model.po.UserPO;
import com.guangying.domain.model.vo.UserVO;
import com.guangying.service.auth.LoginValidationChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 用户原子服务
 * <p>
 * 使用细粒度锁（per-account lock）防止同一账号并发注册导致重复创建。
 * ConcurrentHashMap 存储 account → ReentrantLock 的映射，
 * 仅对相同账号加锁，不影响不同账号的并发注册。
 * </p>
 */
@Slf4j
@Service
public class UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private LoginValidationChain loginValidationChain;

    /**
     * 细粒度注册锁 — 按账号维度加锁
     * 避免 synchronized(this) 或全局锁导致的性能瓶颈
     */
    private final ConcurrentHashMap<String, ReentrantLock> registerLocks = new ConcurrentHashMap<>();

    /**
     * 用户注册
     * <p>
     * 1. 获取该账号的细粒度锁
     * 2. 锁内检查账号是否已存在（防止并发注册同一账号）
     * 3. BCrypt 加密密码后入库
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(UserRegisterDTO dto) {
        String account = dto.getAccount().trim();
        ReentrantLock lock = registerLocks.computeIfAbsent(account, k -> new ReentrantLock());

        lock.lock();
        try {
            log.info("用户注册: account={}", account);

            // 校验邀请码
            if (!"lpf".equals(dto.getInviteCode())) {
                throw new BizException(ResponseCodeEnum.BAD_REQUEST, "邀请码错误");
            }

            // 检查账号是否已存在
            UserPO existing = findByAccount(account);
            if (existing != null) {
                throw new BizException(ResponseCodeEnum.CONFLICT, "该账号已被注册");
            }

            // 创建用户
            UserPO user = new UserPO();
            user.setAccount(account);
            user.setPassword(PasswordUtil.encode(dto.getPassword()));
            user.setUserNick(dto.getUserNick() != null ? dto.getUserNick() : account);
            user.setUserHeadImg(CommonConstants.DEFAULT_HEAD_IMG);
            user.setPoints(500);

            userMapper.insert(user);
            log.info("用户注册成功: id={}, account={}, points=500", user.getId(), account);

            return toVO(user, true);
        } finally {
            lock.unlock();
            // 清理锁对象防止内存泄漏（仅在无竞争时移除）
            registerLocks.remove(account, lock);
        }
    }

    /**
     * 用户登录
     */
    public UserVO login(UserLoginDTO dto) {
        log.info("用户登录: account={}", dto == null ? null : dto.getAccount());

        UserPO user = loginValidationChain.authenticate(dto);

        log.info("用户登录成功: id={}", user.getId());
        return toVO(user, true);
    }

    /**
     * 根据账号查找用户
     */
    public UserPO findByAccount(String account) {
        LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPO::getAccount, account)
                .eq(UserPO::getDeleted, 0);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 根据ID获取用户信息（不含token）
     */
    public UserVO getUserInfo(Long userId) {
        UserPO po = userMapper.selectById(userId);
        if (po == null || po.getDeleted() == 1) return null;
        return toVO(po, false);
    }

    private UserVO toVO(UserPO po, boolean includeToken) {
        UserVO vo = new UserVO();
        vo.setId(po.getId());
        vo.setAccount(po.getAccount());
        vo.setUserNick(po.getUserNick());
        vo.setUserHeadImg(po.getUserHeadImg());
        vo.setPoints(po.getPoints());
        if (includeToken) {
            vo.setToken(jwtUtil.generateToken(po.getId(), po.getAccount()));
        }
        return vo;
    }
}
