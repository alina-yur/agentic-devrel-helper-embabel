package com.example.devrelhelper;

import com.example.devrelhelper.model.BlogSection;
import com.example.devrelhelper.model.PullRequestResult;
import com.example.devrelhelper.model.Talk;
import com.example.devrelhelper.model.TalkWrapped;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(DevrelHelperRuntimeHints.class)
@RegisterReflectionForBinding({Talk.class, TalkWrapped.class, BlogSection.class, PullRequestResult.class})
public class DevrelHelperApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(DevrelHelperApplication.class, args);
		int exitCode = SpringApplication.exit(context);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}
}
