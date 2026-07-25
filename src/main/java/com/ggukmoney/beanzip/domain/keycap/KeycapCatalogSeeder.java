package com.ggukmoney.beanzip.domain.keycap;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap.Grade;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 시즌1 카탈로그는 현재 실제 이미지·사운드 자산이 확보된 14종만 시드한다 — Figma SPEC(548:13)은 "시즌1 24종"이라
 * 적혀 있지만 자산이 14종만 존재해 14종으로 축소 운영하기로 결정함(§11.12 참고).
 */
@Component
@RequiredArgsConstructor
public class KeycapCatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KeycapCatalogSeeder.class);
    private static final String IMAGE_BASE_URL = "https://d1ial9ngbs7qry.cloudfront.net/keycaps/webp/";
    private static final String SOUND_BASE_URL = "https://d1ial9ngbs7qry.cloudfront.net/song/";
    private static final int REQUIRED_SHARD_COUNT = 20;
    private static final int SEASON = 1;

    private static final List<CatalogEntry> CATALOG = List.of(
            new CatalogEntry("biscuit", "비스킷 키캡", Grade.RARE, "c01_biscuit_crack.m4a"),
            new CatalogEntry("cheer", "치어 키캡", Grade.COMMON, "37_chime.m4a"),
            new CatalogEntry("dolphin", "돌고래 키캡", Grade.COMMON, "35_droplet.m4a"),
            new CatalogEntry("earth", "지구 키캡", Grade.EPIC, "c10_earth_thunder.m4a"),
            new CatalogEntry("jellyfoot", "젤리발 키캡", Grade.RARE, "55_soft_tap.m4a"),
            new CatalogEntry("lucky", "럭키 키캡", Grade.COMMON, "31_chord.m4a"),
            new CatalogEntry("main", "메인 키캡", Grade.COMMON, "m05_black.m4a"),
            new CatalogEntry("moon", "달 키캡", Grade.EPIC, "c04_nature_breeze.m4a"),
            new CatalogEntry("pinkjelly", "핑크젤리 키캡", Grade.RARE, "36_wobble.m4a"),
            new CatalogEntry("pudding", "푸딩 키캡", Grade.LEGENDARY, "c02_slime_squish.m4a"),
            new CatalogEntry("redlego", "레드레고 키캡", Grade.COMMON, "m05_black.m4a"),
            new CatalogEntry("yellowlego", "옐로우레고 키캡", Grade.COMMON, "m05_black.m4a"),
            new CatalogEntry("radio", "라디오 키캡", Grade.LEGENDARY, "c07_earth_radio.m4a"),
            new CatalogEntry("space", "스페이스 키캡", Grade.EPIC, "c11_elephant.m4a")
    );

    private final KeycapRepository keycapRepository;

    @Override
    public void run(String... args) {
        try {
            int sortOrder = 1;
            for (CatalogEntry entry : CATALOG) {
                seedIfMissing(entry, sortOrder);
                sortOrder++;
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to seed keycap catalog; existing rows will be used until this succeeds", exception);
        }
    }

    private void seedIfMissing(CatalogEntry entry, int sortOrder) {
        if (keycapRepository.existsByCode(entry.code())) {
            return;
        }
        keycapRepository.save(Keycap.createFor(
                entry.code(),
                entry.name(),
                entry.grade(),
                REQUIRED_SHARD_COUNT,
                SEASON,
                IMAGE_BASE_URL + entry.code() + "Keycap.webp",
                SOUND_BASE_URL + entry.soundFileName(),
                sortOrder
        ));
    }

    private record CatalogEntry(String code, String name, Grade grade, String soundFileName) {
    }
}
