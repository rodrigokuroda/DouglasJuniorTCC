/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package br.edu.utfpr.cm.JGitMinerWeb.services.matrix;

import br.edu.utfpr.cm.JGitMinerWeb.dao.GenericDao;
import br.edu.utfpr.cm.JGitMinerWeb.model.matrix.EntityMatrix;
import br.edu.utfpr.cm.JGitMinerWeb.model.miner.EntityComment;
import br.edu.utfpr.cm.JGitMinerWeb.model.miner.EntityCommitFile;
import br.edu.utfpr.cm.JGitMinerWeb.model.miner.EntityIssue;
import br.edu.utfpr.cm.JGitMinerWeb.model.miner.EntityPullRequest;
import br.edu.utfpr.cm.JGitMinerWeb.model.miner.EntityRepository;
import br.edu.utfpr.cm.JGitMinerWeb.model.miner.EntityRepositoryCommit;
import br.edu.utfpr.cm.JGitMinerWeb.services.matrix.auxiliary.AuxFileFile;
import br.edu.utfpr.cm.JGitMinerWeb.services.matrix.auxiliary.AuxUserFileFileUserDirectional;
import br.edu.utfpr.cm.JGitMinerWeb.services.matrix.auxiliary.AuxUserUserDirectional;
import br.edu.utfpr.cm.JGitMinerWeb.util.OutLog;
import br.edu.utfpr.cm.JGitMinerWeb.util.Util;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author douglas
 */
public class UserCommentedSamePairOfFileInDateServices extends AbstractMatrixServices {

    public UserCommentedSamePairOfFileInDateServices(GenericDao dao, OutLog out) {
        super(dao, out);
    }

    public UserCommentedSamePairOfFileInDateServices(GenericDao dao, EntityRepository repository, List<EntityMatrix> matricesToSave, Map params, OutLog out) {
        super(dao, repository, matricesToSave, params, out);
    }

    private String getPrefixFile() {
        return params.get("prefixFile") + "%";
    }

    private String getSuffixFile() {
        return "%" + params.get("suffixFile");
    }

    private Integer getMaxFilesPerCommit() {
        return Util.stringToInteger(params.get("maxFilesPerCommit") + "");
    }

    private List<String> getFilesName() {
        List<String> filesName = new ArrayList<>();
        for (String fileName : (params.get("filesName") + "").split("\n")) {
            fileName = fileName.trim();
            if (!fileName.isEmpty()) {
                filesName.add(fileName);
            }
        }
        return filesName;
    }

    public Date getBeginDate() {
        return getDateParam("beginDate");
    }

    public Date getEndDate() {
        return getDateParam("endDate");
    }

    @Override
    public void run() {
        System.out.println(params);

        if (getRepository() == null) {
            throw new IllegalArgumentException("Parâmetro Repository não pode ser nulo.");
        }

        String jpql = "";

        List<AuxUserFileFileUserDirectional> result = new ArrayList<>();

        List<String> filesName = getFilesName();
        String prefix = getPrefixFile();
        String suffix = getSuffixFile();

        // select a issue/pullrequest comments
        List<EntityIssue> issuesCommenteds;
        if (!filesName.isEmpty()) {
            jpql = "SELECT DISTINCT i "
                    + "FROM "
                    + "EntityPullRequest p JOIN p.issue i JOIN p.repositoryCommits rc JOIN rc.files f "
                    + "WHERE "
                    + "p.repository = :repo AND "
                    + "p.createdAt BETWEEN :beginDate AND :endDate AND "
                    + "i.commentsCount > 1  AND "
                    + "f.filename IN :filesName";

            System.out.println(jpql);

            issuesCommenteds = dao.selectWithParams(jpql,
                    new String[]{"repo", "beginDate", "endDate", "filesName"},
                    new Object[]{getRepository(), getBeginDate(), getEndDate(), filesName});
        } else if (prefix.length() > 1 || suffix.length() > 1) {
            jpql = "SELECT DISTINCT i "
                    + "FROM "
                    + "EntityPullRequest p JOIN p.issue i JOIN p.repositoryCommits rc JOIN rc.files f "
                    + "WHERE "
                    + "p.repository = :repo AND "
                    + "p.createdAt BETWEEN :beginDate AND :endDate AND "
                    + "i.commentsCount > 1  "
                    + (prefix.length() > 1 ? " AND f.filename LIKE :prefix " : "")
                    + (suffix.length() > 1 ? " AND f.filename LIKE :suffix " : "");

            System.out.println(jpql);

            issuesCommenteds = dao.selectWithParams(jpql,
                    new String[]{"repo",
                        "beginDate",
                        "endDate",
                        (prefix.length() > 1 ? "prefix " : "#none#"),
                        (suffix.length() > 1 ? "suffix" : "#none#")},
                    new Object[]{getRepository(),
                        getBeginDate(),
                        getEndDate(),
                        prefix,
                        suffix});
        } else {
            jpql = "SELECT DISTINCT i "
                    + "FROM "
                    + "EntityPullRequest p JOIN p.issue i "
                    + "WHERE "
                    + "p.repository = :repo AND "
                    + "p.createdAt BETWEEN :beginDate AND :endDate AND "
                    + "i.commentsCount > 1 ";

            System.out.println(jpql);

            issuesCommenteds = dao.selectWithParams(jpql,
                    new String[]{"repo", "beginDate", "endDate"},
                    new Object[]{getRepository(), getBeginDate(), getEndDate()});
        }

        out.printLog("Issues comentadas: " + issuesCommenteds.size());

        int count = 1;
        for (EntityIssue issue : issuesCommenteds) {
            out.printLog("##################### NR: " + issue.getNumber() + " URL: " + issue.getUrl());
            out.printLog(count + " of the " + issuesCommenteds.size());

            jpql = "SELECT p "
                    + " FROM EntityPullRequest p "
                    + " WHERE p.repository = :repo "
                    + " AND p.issue = :issue ";

            EntityPullRequest pr = dao.selectOneWithParams(jpql,
                    new String[]{"repo", "issue"},
                    new Object[]{getRepository(), issue});

            if (pr.getRepositoryCommits().isEmpty()) {
                continue;
            }

            out.printLog(pr.getRepositoryCommits().size() + " commits in pull request ");

            List<EntityCommitFile> commitFiles = new ArrayList();
            for (EntityRepositoryCommit comm : pr.getRepositoryCommits()) {
                if (comm.getFiles().size() <= getMaxFilesPerCommit()) {
                    commitFiles.addAll(comm.getFiles());
                }
            }

            out.printLog(commitFiles.size() + " files in pull request ");

            Set<AuxFileFile> tempResultFiles = new HashSet<>();

            for (int i = 0; i < commitFiles.size(); i++) {
                EntityCommitFile file1 = commitFiles.get(i);
                for (int j = i + 1; j < commitFiles.size(); j++) {
                    EntityCommitFile file2 = commitFiles.get(j);
                    if (!file1.equals(file2)
                            && !Util.stringEquals(file1.getFilename(), file2.getFilename())) {
                        tempResultFiles.add(new AuxFileFile(file1.getFilename(), file2.getFilename()));
                    }
                }
            }
            commitFiles.clear();

            jpql = "SELECT c "
                    + " FROM EntityComment c "
                    + " WHERE c.issue = :issue "
                    + " ORDER BY c.createdAt ";

            System.out.println(jpql);

            List<EntityComment> comments = dao.selectWithParams(jpql,
                    new String[]{"issue"},
                    new Object[]{issue});
            out.printLog(comments.size() + " comments");

            List<AuxUserUserDirectional> tempResultUsers = new ArrayList<>();

            for (int k = 0; k < comments.size(); k++) {
                EntityComment iCom = comments.get(k);
                for (int l = k - 1; l >= 0; l--) {
                    EntityComment jCom = comments.get(l);
                    if (iCom.getUser().equals(jCom.getUser())) {
                        break;
                    }
                    boolean contem = false;
                    AuxUserUserDirectional aux = new AuxUserUserDirectional(
                            iCom.getUser().getLogin(),
                            iCom.getUser().getEmail(),
                            jCom.getUser().getLogin(),
                            jCom.getUser().getEmail());
                    for (AuxUserUserDirectional a : tempResultUsers) {
                        if (a.equals(aux)) {
                            a.inc();
                            contem = true;
                            break;
                        }
                    }
                    if (!contem) {
                        tempResultUsers.add(aux);
                    }
                }
            }
            comments.clear();

            for (AuxUserUserDirectional users : tempResultUsers) {
                for (AuxFileFile files : tempResultFiles) {
                    AuxUserFileFileUserDirectional aux = new AuxUserFileFileUserDirectional(
                            users.getUser(),
                            files.getFileName(),
                            files.getFileName2(),
                            users.getUser2(),
                            users.getWeigth());
                    boolean contem = false;
                    for (AuxUserFileFileUserDirectional a : result) {
                        if (a.equals(aux)) {
                            a.inc();
                            contem = true;
                            break;
                        }
                    }
                    if (!contem) {
                        result.add(aux);
                    }
                }
            }

            count++;
            out.printLog("Temp user result: " + result.size());
        }

        System.out.println("Result: " + result.size());

        EntityMatrix matrix = new EntityMatrix();
        matrix.setNodes(objectsToNodes(result));
        matricesToSave.add(matrix);
    }

    @Override
    public String getHeadCSV() {
        return "user;file;file2;user2;weigth";
    }
}
