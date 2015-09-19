package com.beijunyi.parallelgit.filesystem.requests;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.utils.BranchUtils;
import com.beijunyi.parallelgit.utils.CommitUtils;
import com.beijunyi.parallelgit.utils.RefUtils;
import com.beijunyi.parallelgit.utils.exception.RefUpdateValidator;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;

public final class CommitRequest extends GitFileSystemRequest<RevCommit> {

  private final String branchRef;
  private final RevCommit commit;
  private PersonIdent author;
  private String authorName;
  private String authorEmail;
  private PersonIdent committer;
  private String committerName;
  private String committerEmail;
  private String message;
  private List<AnyObjectId> parents;
  private boolean amend = false;
  private boolean allowEmpty = false;
  private String refLog;

  private CommitRequest(@Nonnull GitFileSystem gfs) {
    super(gfs);
    String branch = gfs.getBranch();
    branchRef = branch != null ? RefUtils.ensureBranchRefName(branch) : null;
    commit = gfs.getCommit();
  }

  @Nonnull
  static CommitRequest prepare(@Nonnull GitFileSystem gfs) {
    return new CommitRequest(gfs);
  }

  @Nonnull
  public CommitRequest author(@Nullable PersonIdent author) {
    this.author = author;
    return this;
  }

  @Nonnull
  public CommitRequest author(@Nullable String name, @Nullable String email) {
    this.authorName = name;
    this.authorEmail = email;
    return this;
  }

  @Nonnull
  public CommitRequest committer(@Nullable PersonIdent committer) {
    this.committer = committer;
    return this;
  }

  @Nonnull
  public CommitRequest committer(@Nullable String name, @Nullable String email) {
    this.committerName = name;
    this.committerEmail = email;
    return this;
  }

  @Nonnull
  public CommitRequest message(@Nullable String message) {
    this.message = message;
    return this;
  }

  @Nonnull
  public CommitRequest amend(boolean amend) {
    this.amend = amend;
    return this;
  }

  @Nonnull
  public CommitRequest allowEmpty(boolean allowEmpty) {
    this.allowEmpty = allowEmpty;
    return this;
  }

  @Nonnull
  public CommitRequest refLog(@Nullable String refLog) {
    this.refLog = refLog;
    return this;
  }

  @Nonnull
  private RevCommit amendedCommit() {
    if(commit == null)
      throw new IllegalStateException("No commit to amend");
    return commit;
  }

  private void prepareCommitter() {
    if(committer == null) {
      if(committerName != null && committerEmail != null)
        committer = new PersonIdent(committerName, committerEmail);
      else if(committerName == null && committerEmail == null)
        committer = new PersonIdent(repository);
      else
        throw new IllegalStateException();
    }
  }

  private void prepareAuthor() {
    if(author == null) {
      if(!amend) {
        if(authorName != null && authorEmail != null)
          author = new PersonIdent(authorName, authorEmail);
        else if(authorName == null && authorEmail == null && committer != null)
          author = committer;
        else
          throw new IllegalStateException();
      } else {
        RevCommit amendedCommit = amendedCommit();
        PersonIdent amendedAuthor = amendedCommit.getAuthorIdent();
        author = new PersonIdent(authorName != null ? authorName : amendedAuthor.getName(),
                                  authorEmail != null ? authorEmail : amendedAuthor.getEmailAddress());
      }
    }
  }

  private void prepareParents() {
    if(parents == null) {
      if(!amend) {
        if(commit != null)
          parents = Collections.<AnyObjectId>singletonList(commit);
        else
          parents = Collections.emptyList();
      } else
        parents = Arrays.<AnyObjectId>asList(amendedCommit().getParents());
    }
  }

  private void updateRef(@Nonnull AnyObjectId head) throws IOException {
    if(refLog != null)
      BranchUtils.setBranchHead(branchRef, head, repository, refLog, true);
    else if(amend)
      BranchUtils.amendBranchHead(branchRef, head, repository);
    else if(commit != null)
      BranchUtils.commitBranchHead(branchRef, head, repository);
    else
      BranchUtils.initBranchHead(branchRef, head, repository);
  }

  private void updateFileSystem(@Nonnull RevCommit head) {
    gfs.setCommit(head);
  }

  @Nullable
  @Override
  public RevCommit doExecute() throws IOException {
    prepareCommitter();
    prepareAuthor();
    prepareParents();
    AnyObjectId tree = gfs.persist();
    if(!allowEmpty && !amend && tree.equals(commit.getTree()))
      return null;
    AnyObjectId resultCommitId = CommitUtils.createCommit(repository, tree, author, committer, message, parents);
    updateRef(resultCommitId);
    RevCommit resultCommit = CommitUtils.getCommit(resultCommitId, repository);
    updateFileSystem(resultCommit);
    return resultCommit;
  }
}
