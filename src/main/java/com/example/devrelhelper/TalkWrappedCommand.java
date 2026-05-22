package com.example.devrelhelper;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.example.devrelhelper.model.PullRequestResult;
import com.example.devrelhelper.model.Talk;
import com.example.devrelhelper.model.TalkWrapped;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Component
public class TalkWrappedCommand implements CommandLineRunner, ExitCodeGenerator {

    private final AgentPlatform agentPlatform;
    private final Environment environment;
    private int exitCode;

    public TalkWrappedCommand(AgentPlatform agentPlatform, Environment environment) {
        this.agentPlatform = agentPlatform;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        if (isHelp(args)) {
            printUsage();
            return;
        }

        String workflow = setting("devrel.workflow", "wrapped").toLowerCase();
        if ("none".equals(workflow) || "off".equals(workflow)) {
            System.out.println("No workflow selected. Use --devrel.workflow=wrapped, pr, or all.");
            return;
        }

        Talk talk = talkFromEnvironment();

        if ("wrapped".equals(workflow) || "all".equals(workflow)) {
            if (!hasOpenAiApiKey()) {
                exitCode = 2;
                System.err.println("Missing OPENAI_API_KEY. Set it before generating talk-wrapped.txt.");
                System.err.println("Example: OPENAI_API_KEY=... ./target/demo");
                return;
            }
            writeTalkWrapped(talk);
        }

        if ("pr".equals(workflow) || "all".equals(workflow)) {
            if (!hasGitHubToken()) {
                exitCode = 2;
                System.err.println("Missing GITHUB_TOKEN. Set it before creating a public-speaking pull request.");
                System.err.println("Example: GITHUB_TOKEN=... ./target/demo --devrel.workflow=pr");
                return;
            }
            createPullRequest(talk);
        }

        if (!List.of("wrapped", "pr", "all").contains(workflow)) {
            exitCode = 2;
            System.err.println("Unknown workflow: " + workflow);
            System.err.println("Use --devrel.workflow=wrapped, pr, all, or none.");
            return;
        }
    }

    private Talk talkFromEnvironment() {
        return new Talk(
                setting("TITLE", "GraalVM in practice"),
                setting("CONF", setting("CONFERENCE", "Devoxx Morocco 2025")),
                setting("LOCATION", "Marrakesh, Morocco"),
                setting("CONFERENCE_URL", ""),
                demos());
    }

    private void writeTalkWrapped(Talk talk) throws Exception {
        TalkWrapped out = AgentInvocation.create(agentPlatform, TalkWrapped.class).invoke(talk);
        var text = TalkWrappedService.toText(talk, out);
        Path output = Path.of(setting("devrel.output-file", "talk-wrapped.txt"));
        Files.writeString(output, text);
        System.out.println("Generated " + output);
    }

    private void createPullRequest(Talk talk) {
        PullRequestResult result = AgentInvocation.create(agentPlatform, PullRequestResult.class).invoke(talk);
        System.out.println("Created pull request: " + result.url());
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private boolean hasOpenAiApiKey() {
        String apiKey = environment.getProperty("OPENAI_API_KEY");
        return StringUtils.hasText(apiKey) && !"demo-key".equals(apiKey);
    }

    private boolean hasGitHubToken() {
        return StringUtils.hasText(environment.getProperty("GITHUB_TOKEN"))
                || StringUtils.hasText(environment.getProperty("github.token"));
    }

    private String setting(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    private List<String> demos() {
        return Arrays.stream(setting("DEMOS", "https://github.com/alina-yur/graalvm-in-practice").split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private boolean isHelp(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> "--help".equals(arg) || "-h".equals(arg));
    }

    private void printUsage() {
        System.out.println("""
                Usage:
                  OPENAI_API_KEY=... ./target/demo
                  GITHUB_TOKEN=... ./target/demo --devrel.workflow=pr
                  OPENAI_API_KEY=... GITHUB_TOKEN=... ./target/demo --devrel.workflow=all

                Optional inputs:
                  TITLE="GraalVM in practice"
                  CONF="Devoxx Morocco 2025"
                  LOCATION="Marrakesh, Morocco"
                  CONFERENCE_URL="https://..."
                  DEMOS="https://github.com/alina-yur/graalvm-in-practice"

                Options:
                  --devrel.workflow=wrapped            Generate talk-wrapped.txt (default).
                  --devrel.workflow=pr                 Open a public-speaking pull request.
                  --devrel.workflow=all                Run both workflows.
                  --devrel.workflow=none               Start and exit without work.
                """);
    }
}
