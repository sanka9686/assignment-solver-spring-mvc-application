package com.assignment.service;

import com.assignment.exception.InputFailedException;
import com.assignment.model.AuthenticationUser;
import com.assignment.model.User;
import com.assignment.model.dto.UserDto;
import com.assignment.model.dto.UserGetDto;
import com.assignment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class UserService implements UserDetailsService, Compress {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByEmail(email);
        user.orElseThrow(() -> new UsernameNotFoundException("username/email not found"));
        return user.map(AuthenticationUser::new).get();
    }

    public boolean initiateUser(UserDto user, MultipartFile image) throws InputFailedException {
        try {
            String password = passwordEncoder.encode(user.getPassword());
            logger.info("initiate user");
            User _user = new User();
            _user.setEmail(user.getEmail());
            _user.setPassword(password);
            _user.setTwoFactor(user.isTwoFactor());
            _user.setImage(Compress.compressBytes(image.getBytes()));
            _user.setContentType(image.getContentType());
            _user.setOriginalName(image.getOriginalFilename());
            userRepository.save(_user);
            logger.info("finished saving user");
        }catch (Exception e){
            throw new InputFailedException();
        }
        return true;
    }

    public boolean checkUsername(String email) {
        Optional<User> checkedEmail = userRepository.findByEmail(email);
        return checkedEmail.isPresent();
    }

    public UserGetDto getCurrentUser()throws InputFailedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = userRepository.findByEmail(auth.getName());
        User u = user.get();
        return new UserGetDto(u.getEmail(), Compress.decompressBytes(u.getImage()), u.getOriginalName(), u.getContentType(), u.isTwoFactor());


    }

    public long getAllMembersCount() {
        return userRepository.findAll().stream().mapToLong(User::getUserId).count();
    }

}

