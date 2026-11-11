package com.example.urlshortener.repository;

import com.example.urlshortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByCode(String code);

    boolean existsByCode(String code);


    @Transactional
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.code = :code")
    void incrementClickCount(String code);
}
