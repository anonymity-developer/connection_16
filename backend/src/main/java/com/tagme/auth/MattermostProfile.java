package com.tagme.auth;

import java.util.*;
import java.util.regex.Pattern;

/** Mattermost profile metadata, not independently verified legal identity. */
public record MattermostProfile(String realName,String region,int classNo,long pictureUpdatedAt) {
    private static final Pattern NAME=Pattern.compile("^([^\\[\\]\\r\\n]{1,100}?)\\s*\\[\\s*([가-힣]{2,10})\\s*_\\s*([0-9]{1,4})\\s*반?\\s*]$");
    public static MattermostProfile parse(String first,String last,String nickname,long picture) {
        // Prefer the account's actual name fields. Only use nickname if no full name matches.
        String f=first==null?"":first.strip(),l=last==null?"":last.strip();
        for(String candidate:List.of((l+f).strip(),(f+" "+l).strip(),f,l)) {
            var found=match(candidate,picture);if(found!=null)return found;
        }
        return match(nickname==null?"":nickname.strip(),picture);
    }
    private static MattermostProfile match(String value,long picture) {
        var m=NAME.matcher(value).matches()?NAME.matcher(value):null;if(m==null||!m.matches())return null;
        String name=m.group(1).strip();int number=Integer.parseInt(m.group(3));
        if(name.isEmpty()||name.chars().anyMatch(c->Character.isISOControl(c))||number<1)return null;
        return new MattermostProfile(name,m.group(2),number,Math.max(0,picture));
    }
    @Override public String toString() { return "MattermostProfile[redacted]"; }
}
