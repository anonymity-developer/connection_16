package com.tagme.profile;

import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProfilePhotoService {
    private final AuthRepository accounts;
    private final ParticipationRepository participation;
    private final MattermostClient mattermost;
    private final AuthRateLimiter limits;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public ProfilePhotoService(AuthRepository accounts,ParticipationRepository participation,MattermostClient mattermost,AuthRateLimiter limits,JdbcTemplate db,PlatformTransactionManager manager) { this.accounts=accounts;this.participation=participation;this.mattermost=mattermost;this.limits=limits;this.db=db;this.tx=new TransactionTemplate(manager); }
    private static AuthException missing(){return new AuthException(404,"PROFILE_PHOTO_NOT_FOUND","프로필 사진을 찾을 수 없어요.");}
    private String authorize(AuthPrincipal actor,long target,boolean own) {
        return tx.execute(s->{
            java.util.stream.LongStream.of(actor.userId(),target).distinct().sorted().forEach(accounts::lockUser);participation.lockPrincipal(actor);
            var account=accounts.byId(target).orElseThrow(ProfilePhotoService::missing);
            if(!"ACTIVE".equals(account.status())||!accounts.profileIdentity(target).ready()||accounts.profileIdentity(target).pictureVersion()<1)throw missing();
            if(!own){
                participation.requireOpen(false);
                if(!participation.eligible(actor.userId())||!participation.eligible(target))throw missing();
                if(target!=actor.userId()&&db.queryForObject("SELECT COUNT(*) FROM profile_views WHERE viewer_id=? AND viewed_id=?",Integer.class,actor.userId(),target)!=1)throw missing();
            }
            return account.mattermostId();
        });
    }
    public byte[] photo(AuthPrincipal actor,long target,boolean own) {
        if(target<1||own&&target!=actor.userId())throw missing();
        String external=authorize(actor,target,own);limits.check("profile-photo",Long.toString(actor.userId()),60,60);
        byte[] image=mattermost.photo(external); // Bounded external I/O outside every DB lock.
        if(image==null)throw missing();
        if(!Objects.equals(external,authorize(actor,target,own)))throw missing();
        return image;
    }
}
