package com.example.devrelhelper;

import com.example.devrelhelper.model.*;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Agent(
        name = "talk-promotion-agent",
        description = "Generate practical DevRel promotion assets from talk metadata and demo links."
)
public class TalkWrappedService {

    @Action(description = "Generate structured talk promotion assets.")
    @AchievesGoal(description = "Create tweets and a blog outline for a conference talk.")
    public TalkWrapped generate(Talk in, Ai ai) {
        var prompt = template()
                .replace("${title}", orEmpty(in.title()))
                .replace("${conference}", orEmpty(in.conf()))
                .replace("${location}", orEmpty(in.location()))
                .replace("${demos}", String.join(", ", in.demos() == null ? List.of() : in.demos()));
        return ai.withAutoLlm().creating(TalkWrapped.class).fromPrompt(prompt);
    }

    public static String toText(Talk in, TalkWrapped t) {
        var b = new StringBuilder();
        b.append("TITLE: ").append(in.title()).append("\n");
        b.append("CONFERENCE: ").append(in.conf()).append("\n");
        b.append("LOCATION: ").append(in.location()).append("\n");
        if (in.conferenceUrl() != null && !in.conferenceUrl().isBlank()) {
            b.append("CONFERENCE URL: ").append(in.conferenceUrl()).append("\n");
        }
        b.append("\n");

        b.append("TWEETS:\n");
        t.tweets().forEach(s -> b.append("- ").append(s).append("\n"));
        b.append("\n");

        b.append("BLOG TITLE:\n").append(t.blogTitle()).append("\n\n");
        b.append("BLOG OVERVIEW:\n").append(t.blogOverview()).append("\n\n");

        b.append("BLOG SECTIONS:\n");
        for (var s : t.blogSections()) {
            b.append(s.heading()).append("\n");
            for (var bl : s.bullets())
                b.append("- ").append(bl).append("\n");
            b.append("\n");
        }

        if (in.demos() != null && !in.demos().isEmpty()) {
            b.append("DEMOS:\n");
            in.demos().forEach(d -> b.append(d.trim()).append("\n"));
        }
        return b.toString();
    }

    private String template() {
        try {
            var r = new ClassPathResource("prompts/talk_wrapped.txt");
            return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
