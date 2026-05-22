package com.example.devrelhelper.model;

import java.util.List;

public record Talk(String title, String conf, String location, String conferenceUrl, List<String> demos) {
}
