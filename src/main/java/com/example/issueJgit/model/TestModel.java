package com.example.issueJgit.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class TestModel {
	private String category;
    private String status;
    private String region;
    private String type;
    private String fullName;
}
