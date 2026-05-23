package com.example.dto.response;

import com.example.domain.SocialPlatform;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLinkResponse {

    private SocialPlatform platform;
    private String url;
}
