package com.supplychain.controltower.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.supplychain.controltower.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long id;
    private String username;
    private String email;

    @JsonIgnore
    private String password;

    private Long companyId;
    private String companyCode;
    private String companyName;
    private String companyStatus;

    private Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String username, String email, String password,
                         Long companyId, String companyCode, String companyStatus,
                         Collection<? extends GrantedAuthority> authorities) {
        this(id, username, email, password, companyId, companyCode, "Demo Supply Chain Corp", companyStatus, authorities);
    }

    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(user.getRole().name()));
        Long cId = user.getCompany() != null ? user.getCompany().getId() : 1L;
        String cCode = user.getCompany() != null ? user.getCompany().getCode() : "DEMO";
        String cName = user.getCompany() != null && user.getCompany().getName() != null
                ? user.getCompany().getName()
                : "Demo Supply Chain Corp";
        String cStatus = user.getCompany() != null ? user.getCompany().getStatus() : "ACTIVE";

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                cId,
                cCode,
                cName,
                cStatus,
                authorities
        );
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return companyStatus == null || !"SUSPENDED".equalsIgnoreCase(companyStatus);
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return companyStatus == null || !"SUSPENDED".equalsIgnoreCase(companyStatus);
    }
}
