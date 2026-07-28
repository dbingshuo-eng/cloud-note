package com.clouddisk.dto;

import java.util.List;

public record FileBatchDeleteRequest(List<Long> ids) {
}
