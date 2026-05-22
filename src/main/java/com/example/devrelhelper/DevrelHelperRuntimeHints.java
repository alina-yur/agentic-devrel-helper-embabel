package com.example.devrelhelper;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class DevrelHelperRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(TalkWrappedService.class, MemberCategory.INVOKE_DECLARED_METHODS);
        hints.reflection().registerType(GitHubPRService.class, MemberCategory.INVOKE_DECLARED_METHODS);
        hints.resources().registerPattern("prompts/talk_wrapped.txt");
    }
}
