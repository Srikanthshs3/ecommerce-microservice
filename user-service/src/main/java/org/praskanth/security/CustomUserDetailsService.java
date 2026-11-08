package org.praskanth.security;


import lombok.RequiredArgsConstructor;
import org.praskanth.entity.User;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.UserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userRepo.findByEmail(username)
                .orElseThrow(()-> new NotFoundException("We couldn't find an account with that email."));

        return AuthUser.builder().user(user).build();
    }
}
