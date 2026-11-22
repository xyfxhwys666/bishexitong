package com.xyfxhwys.faceprivacy.task;

import java.util.List;

public record BatchView(int total, List<TaskSummary> tasks) {
}
