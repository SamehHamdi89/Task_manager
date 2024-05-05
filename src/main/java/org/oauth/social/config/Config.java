package org.oauth.social.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class Config extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        // Disable CSRF protection
        http.csrf().disable();

        // Pages that do not require login
        http.authorizeRequests().antMatchers("/", "/login", "/logout").permitAll();

        // AccessDeniedPage 
        http.authorizeRequests().and().exceptionHandling().accessDeniedPage("/403");


        // Logout 
        http.authorizeRequests().and().logout().logoutUrl("/logout").logoutSuccessUrl("/");

    }

}
