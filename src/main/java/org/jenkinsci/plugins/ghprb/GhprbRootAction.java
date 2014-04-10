package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
@Extension
public class GhprbRootAction implements UnprotectedRootAction {
    static final String URL = "ghprbhook";
    private static final Logger logger = Logger.getLogger(GhprbRootAction.class.getName());

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URL;
    }

    public void doIndex(StaplerRequest req, StaplerResponse resp) {
        String event = req.getHeader("X-GitHub-Event");
        String payload = req.getParameter("payload");
        if (payload == null) {
            logger.log(Level.SEVERE, "Request doesn't contain payload.");
            return;
        }

        GhprbGitHub gh = GhprbTrigger.getDscp().getGitHub();

        logger.log(Level.INFO, "Got payload event: {0}", event);
        try {
            if ("issue_comment".equals(event)) {
                GHEventPayload.IssueComment issueComment = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class);
                for (GhprbRepository repo : getRepos(issueComment.getRepository())) {
                    repo.onIssueCommentHook(issueComment);
                }
            } else if ("pull_request".equals(event)) {
                GHEventPayload.PullRequest pr = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
                for (GhprbRepository repo : getRepos(pr.getPullRequest().getRepository())) {
                    repo.onPullRequestHook(pr);
                }
            } else {
                logger.log(Level.WARNING, "Request not known");
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to parse github hook payload.", ex);
        }
    }

    private Set<GhprbRepository> getRepos(GHRepository repo) throws IOException {
        try {
            return GhprbRepository.getRepos(repo.getOwner().getLogin(), repo.getName());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Can't get a valid owner for repo");
            // this normally happens due to missing "login" field in the owner of the repo
            // when the repo is inside of an organisation account. The only field which doesn't
            // rely on the owner.login (which would throw a null pointer exception) is the "html_url"
            // field. So we try to parse the owner out of that here until github fixes his api
            String repoUrl = repo.getUrl();
            if (repoUrl.endsWith("/")) {// strip off trailing slash if any
                repoUrl = repoUrl.substring(0, repoUrl.length() - 2);
            }
            int slashIndex = repoUrl.lastIndexOf('/');
            String owner = repoUrl.substring(slashIndex + 1);
            logger.log(Level.INFO, "Parsed {0} from {1}", new Object[]{owner, repoUrl});
            return GhprbRepository.getRepos(owner, repo.getName());
        }
    }

}
