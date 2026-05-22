package com.example.devrelhelper;

import com.example.devrelhelper.model.PullRequestResult;
import com.example.devrelhelper.model.Talk;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Service
@Agent(
        name = "public-speaking-pr-agent",
        description = "Create a GitHub pull request that adds an upcoming talk to the public speaking repository."
)
@RegisterReflectionForBinding({
        GitHubPRService.GitHubContent.class,
        GitHubPRService.GitHubRef.class,
        GitHubPRService.GitHubObject.class,
        GitHubPRService.GitHubPrResponse.class
})
public class GitHubPRService {

    public record GitHubContent(String content, String sha) {
    }

    public record GitHubRef(GitHubObject object) {
    }

    public record GitHubObject(String sha) {
    }

    public record GitHubPrResponse(String html_url) {
    }

    private final RestClient restClient;
    private final String repoOwner;
    private final String repoName;
    private final String filePath;
    private final String baseBranch;

    public GitHubPRService(
            RestClient.Builder builder,
            @Value("${github.token:}") String githubToken,
            @Value("${github.repo-owner:alina-yur}") String repoOwner,
            @Value("${github.repo-name:public-speaking}") String repoName,
            @Value("${github.file-path:README.md}") String filePath,
            @Value("${github.base-branch:main}") String baseBranch) {
        var clientBuilder = builder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        if (StringUtils.hasText(githubToken)) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
        }

        this.restClient = clientBuilder.build();
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.filePath = filePath;
        this.baseBranch = baseBranch;
    }

    @Action(description = "Open a pull request that adds the talk to the public speaking README.")
    @AchievesGoal(description = "Create a GitHub pull request for an upcoming talk.")
    public PullRequestResult createPR(Talk talk) {
        GitHubContent fileInfo = getFileContent(baseBranch);
        String baseSha = getBranchSha(baseBranch);

        String currentContent = decodeContent(fileInfo.content());
        String updatedContent = insertTalk(currentContent, talk);

        String branchName = "feat/talk-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        createBranch(branchName, baseSha);
        commitFile(branchName, updatedContent, fileInfo.sha(), talk);

        return new PullRequestResult(createPullRequest(branchName, talk));
    }

    private String decodeContent(String encoded) {
        String clean = encoded.replaceAll("\\s", "");
        return new String(Base64.getDecoder().decode(clean), StandardCharsets.UTF_8);
    }

    private String insertTalk(String content, Talk talk) {
        String talkLabel = StringUtils.hasText(talk.conferenceUrl())
                ? String.format("[%s](%s)", talk.title(), talk.conferenceUrl())
                : talk.title();
        String entry = String.format("* %s (%s, %s)%n", talkLabel, talk.conf(), talk.location());
        String targetHeader = "## Upcoming talks";

        int headerIndex = content.indexOf(targetHeader);
        if (headerIndex == -1) {
            return targetHeader + "\n\n" + entry + "\n" + content;
        }

        int lineEnd = content.indexOf('\n', headerIndex);
        if (lineEnd == -1) {
            return content + "\n" + entry;
        }

        int insertPos = lineEnd + 1;
        while (insertPos < content.length() && content.charAt(insertPos) == '\n') {
            insertPos++;
        }

        return content.substring(0, insertPos) + entry + content.substring(insertPos);
    }

    private GitHubContent getFileContent(String ref) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", repoOwner, repoName, filePath, ref)
                .retrieve()
                .body(GitHubContent.class);
    }

    private String getBranchSha(String branch) {
        GitHubRef res = restClient.get()
                .uri("/repos/{owner}/{repo}/git/ref/heads/{branch}", repoOwner, repoName, branch)
                .retrieve()
                .body(GitHubRef.class);
        if (res == null || res.object() == null) {
            throw new IllegalStateException("Failed to retrieve branch SHA for: " + branch);
        }
        return res.object().sha();
    }

    private void createBranch(String branchName, String sha) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/git/refs", repoOwner, repoName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ref", "refs/heads/" + branchName, "sha", sha))
                .retrieve()
                .toBodilessEntity();
    }

    private void commitFile(String branch, String content, String originalSha, Talk talk) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

        restClient.put()
                .uri("/repos/{owner}/{repo}/contents/{path}", repoOwner, repoName, filePath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "message", "Add talk: " + talk.title(),
                        "content", encoded,
                        "sha", originalSha,
                        "branch", branch))
                .retrieve()
                .toBodilessEntity();
    }

    private String createPullRequest(String branch, Talk talk) {
        GitHubPrResponse response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", repoOwner, repoName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", "Add upcoming talk: " + talk.title(),
                        "body", String.format("Adding upcoming talk:%n%n**%s** at %s", talk.title(), talk.conf()),
                        "head", branch,
                        "base", baseBranch))
                .retrieve()
                .body(GitHubPrResponse.class);

        if (response == null) {
            throw new IllegalStateException("Failed to create pull request");
        }
        return response.html_url();
    }
}
