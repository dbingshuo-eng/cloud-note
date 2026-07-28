package com.clouddisk.service;

import com.clouddisk.dto.UserLoginDTO;
import com.clouddisk.entity.User;
import com.clouddisk.mapper.UserMapper;
import com.clouddisk.utils.JwtTokenUtil;
import com.clouddisk.vo.UserInfoVO;
import com.clouddisk.vo.UserLoginVO;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

@Service
public class UserService {

    private final WechatAuthService wechatAuthService;
    private final UserMapper userMapper;
    private final JwtTokenUtil jwtTokenUtil;

    public UserService(WechatAuthService wechatAuthService, UserMapper userMapper, JwtTokenUtil jwtTokenUtil) {
        this.wechatAuthService = wechatAuthService;
        this.userMapper = userMapper;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    public UserLoginVO login(UserLoginDTO request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }

        WechatAuthService.WechatSession session = wechatAuthService.exchangeCode(request.code());
        User user = findOrCreateUser(session.openid());

        String token = jwtTokenUtil.generateToken(user.getId());
        return new UserLoginVO(token, toUserInfo(user));
    }

    private User findOrCreateUser(String openid) {
        User user = userMapper.selectByOpenid(openid);
        if (user != null) {
            return user;
        }

        user = createUser(openid);
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException exception) {
            User recoveredUser = userMapper.selectByOpenid(openid);
            if (recoveredUser != null) {
                return recoveredUser;
            }
            throw WechatLoginException.conflict("User creation conflict, please retry");
        }
    }

    private User createUser(String openid) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setOpenid(openid);
        user.setNickname("");
        user.setAvatar("");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return user;
    }

    private UserInfoVO toUserInfo(User user) {
        return new UserInfoVO(user.getId(), user.getNickname(), user.getAvatar());
    }
}
