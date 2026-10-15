package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "현재 패시브 효과와 미수령 자동 클릭 현황이에요. 조회만으로 지급하거나 정산 체크포인트를 변경하지 않아요.")
public record KeycapPassiveStatusResponse(
        @Schema(description = "신규 패시브 적립과 직접 탭 효과의 활성 여부예요. false여도 비활성화 전에 쌓인 몫은 유지돼요.", example = "false")
        boolean enabled,
        @Schema(description = "현재 장착한 키캡 코드예요. 미장착이면 null이에요.", nullable = true, example = "cheer")
        String equippedKeycapCode,
        @Schema(description = "현재 장착한 키캡의 레벨이에요. 미장착이면 null이에요.", nullable = true, example = "1")
        Integer equippedLevel,
        @Schema(description = "현재 장착 키캡의 레벨에서 계산한 효과예요. 과거 적립분은 기존 체크포인트의 효과로 정산해요.")
        KeycapPassiveEffectResponse effects,
        @Schema(description = "조회 시점에 정산할 수 있는 자동 클릭 수예요. 소수 클릭과 비활성 구간은 제외하고 미수령 적립 상한을 적용한 값이에요.", example = "60")
        int pendingAutoClicks,
        @Schema(description = "이번 정산에 적용할 미수령 자동 클릭 상한이에요. 체크포인트의 하루 자동 클릭 수와 적립 일수 상한을 곱한 값이며 기본 적립 일수는 7일이에요.", example = "420")
        int autoClickAccrualCap,
        @Schema(description = "미수령 자동 클릭이 적립 상한을 초과했는지 여부예요. 정확히 상한과 같으면 false예요.", example = "false")
        boolean capped,
        @Schema(description = "마지막 정산 체크포인트의 서버 시각이에요. 아직 체크포인트가 없으면 null이에요.", nullable = true, example = "2026-10-11T00:00:00Z")
        Instant lastSettledAt,
        @Schema(description = "미수령 자동 클릭을 계산한 서버 기준 시각이에요.", example = "2026-10-12T00:00:00Z")
        Instant asOf
) {}
