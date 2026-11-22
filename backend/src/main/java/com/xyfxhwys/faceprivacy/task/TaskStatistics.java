package com.xyfxhwys.faceprivacy.task;

import java.util.Map;

public record TaskStatistics(
        long total,
        long completed,
        long processing,
        long failed,
        long agentTasks,
        double averageSsim,
        double averagePsnr,
        Map<String, Long> strategyCounts
) {
}
