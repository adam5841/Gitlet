package gitlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Collections;

/** A repository that helps store files written in the directory.
 *  @author Adam
 */
public class Gitlet implements Serializable {
    /** The directory for all files. **/
    static final File CWD = new File(System.getProperty("user.dir"));
    /** Folder for all files created by the repo. **/
    static final File GITLET_FOLDER = new File(".gitlet");
    /** Main folder for all staging files. **/
    static final File STAGING_FOLDER = new File(".gitlet/staging");
    /** Main folder for all blob files. **/
    static final File BLOB_FOLDER = new File(".gitlet/blobs");
    /** Main data structure for staged files. **/
    private HashMap<String, String> _stagedFiles;
    /** Main data structure for removed files. **/
    private ArrayList<String> _removeFiles;
    /** Main data structure to retain the blob files. **/
    private HashMap<String, String> _blobFiles;
    /** Main data structure for all the commits. **/
    private HashMap<String, Commit> _commits;
    /** Main data structure to keep track of all branches. **/
    private HashMap<String, LinkedList<Commit>> _branches;
    /** Name of current branch. **/
    private String _keyToCurrBranch;
    /** Pointer to main branch. **/
    private Commit _master;
    /** Pointer to the head commit. **/
    private Commit _head;
    /** Checks if merge encounters conflict. **/
    private boolean checker = false;
    /** Constructor for Gitlet. **/
    public Gitlet() {
        _head = null;
        _master = null;
    }
    /** Setting up the folders for the repo. **/
    public static void setUpPersistence() throws IOException {
        GITLET_FOLDER.mkdir();
        STAGING_FOLDER.mkdir();
        BLOB_FOLDER.mkdir();
        Commit.COMMIT_FOLDER.mkdir();
    }
    /** Saves all files in the repo.
     * @param git git
     * @param name name
     **/
    public static void serialize(Gitlet git, String name) throws IOException {
        if (git != null) {
            File file = new File(name);
            FileOutputStream fileOutput = new FileOutputStream(file);
            ObjectOutputStream output = new ObjectOutputStream(fileOutput);
            output.writeObject(git);
            output.close();
            fileOutput.close();
        }
    }
    /** Reads the saved files from the git repo.
     * @param  filename filename
     * @return Gitlet file
     **/
    public static Gitlet deserialize(String filename)
            throws ClassNotFoundException {
        Gitlet gitlet = null;
        File file = new File(filename);
        try {
            if (file.exists()) {
                FileInputStream fileInput = new FileInputStream(filename);
                ObjectInputStream inObject = new ObjectInputStream(fileInput);
                gitlet = (Gitlet) inObject.readObject();
                inObject.close();
                fileInput.close();
            }
            return gitlet;
        } catch (IOException exception) {
            return null;
        }
    }
    /** Initializes all variables for the repo.
     * @param  args args
     **/
    public void init(String[] args) throws IOException, ParseException {
        File gitlet = new File(".gitlet");
        if (!gitlet.exists()) {
            setUpPersistence();
            Commit initial = new Commit(new Date(0),
                    "initial commit", new HashMap<>(), null);
            _commits = new HashMap<>();
            _keyToCurrBranch = "master";
            _commits.put(initial.getSha1(),  initial);
            _stagedFiles = new HashMap<>();
            _blobFiles = new HashMap<>();
            _master = initial;
            _head = initial;
            _removeFiles = new ArrayList<>();
            _branches = new HashMap<>();
            _commits.put(initial.getSha1(), initial);
            if (_branches.get(_keyToCurrBranch) != null) {
                _branches.get(_keyToCurrBranch).add(initial);
            } else {
                LinkedList<Commit> commitLink = new LinkedList<>();
                commitLink.add(initial);
                _branches.put(_keyToCurrBranch, commitLink);
            }
        } else {
            System.out.println("A Gitlet version-control "
                    + "system already exists in the current directory.");

        }
    }
    /** Adds files to the staging area.
     * @param  args args
     **/
    public void add(String[] args) throws IOException {
        File actualFile = Utils.join(CWD, args[1]);
        if (!actualFile.exists()) {
            System.out.println("File does not exist");
            return;
        }
        if (_removeFiles.contains(args[1])) {
            _removeFiles.remove(args[1]);
        }
        File stagingFile = Utils.join(STAGING_FOLDER, args[1]);
        stagingFile.createNewFile();
        _stagedFiles.put(args[1], actualFile.getPath());
        if (_head.getFiles().containsKey(args[1])) {
            String pathway = _head.getFiles().get(args[1]);
            File check = new File(pathway);
            if (new File(args[1]).exists() && new File(".gitlet/blobs/"
                    + Utils.sha1(Utils.readContentsAsString
                    (new File(args[1])))).exists()) {
                if (Utils.readContentsAsString(check)
                        .equals(Utils.readContentsAsString(actualFile))) {
                    _stagedFiles.remove(args[1]);
                    return;
                }
            }
        }

        Utils.writeContents(stagingFile,
                Utils.readContentsAsString(actualFile));
    }
    /** Adds files to the blob folder.
     * @param  args args
     **/
    public void commit(String[] args) throws IOException {
        if (_stagedFiles.size() == 0 && _removeFiles.size() == 0) {
            System.out.println("No changes added to the commit");
            return;
        }
        if (args.length == 1 || args[1].equals("")) {
            System.out.println("Please enter a commit message");
            return;
        }

        HashMap<String, String> files = new HashMap<>();
        files.putAll(_head.getFiles());
        for (String s : _stagedFiles.keySet()) {
            File sFile = new File(_stagedFiles.get(s));
            String id = Utils.sha1(Utils.readContentsAsString(sFile));
            files.put(s, ".gitlet/blobs/" + id);
            File blobFile = Utils.join(BLOB_FOLDER, id);
            File currFile = Utils.join(STAGING_FOLDER, s);
            String currString = Utils.readContentsAsString(currFile);
            blobFile.createNewFile();
            Utils.writeContents(blobFile, currString);
        }
        for (String s : _removeFiles) {
            files.remove(s);
        }
        _removeFiles.clear();

        Commit commit = new Commit(new Date(), args[1], files, _head);
        if (_branches.get(_keyToCurrBranch) != null) {
            _branches.get(_keyToCurrBranch).add(commit);
        } else {
            LinkedList<Commit> commitLink = new LinkedList<>();
            commitLink.add(commit);
            _branches.put(_keyToCurrBranch, commitLink);
        }
        for (String s : _stagedFiles.keySet()) {
            File del = new File(".gitlet/staging/" + s);
            del.delete();
        }

        _stagedFiles.clear();

        _head = commit;
        _commits.put(commit.getSha1(), commit);
    }
    /** Commit with merge and commit argument.
     * @param  commit commit
     **/
    public void commitMerge(Commit commit) throws IOException {
        HashMap<String, String> files = new HashMap<>();
        files.putAll(_head.getFiles());
        for (String s : _stagedFiles.keySet()) {
            File sFile = new File(_stagedFiles.get(s));
            String id = Utils.sha1(Utils.readContentsAsString(sFile));
            files.put(s, ".gitlet/blobs/" + id);
            File blobFile = Utils.join(BLOB_FOLDER, id);
            File currFile = Utils.join(STAGING_FOLDER, s);
            String currString = Utils.readContentsAsString(currFile);
            blobFile.createNewFile();
            Utils.writeContents(blobFile, currString);
        }
        for (String s : _removeFiles) {
            files.remove(s);
        }
        _removeFiles.clear();

        if (_branches.get(_keyToCurrBranch) != null) {
            _branches.get(_keyToCurrBranch).add(commit);
        } else {
            LinkedList<Commit> commitLink = new LinkedList<>();
            commitLink.add(commit);
            _branches.put(_keyToCurrBranch, commitLink);
        }
        for (String s : _stagedFiles.keySet()) {
            File del = new File(".gitlet/staging/" + s);
            del.delete();
        }

        _stagedFiles.clear();

        _head = commit;
        _commits.put(commit.getSha1(), commit);
    }
    /** Helper method for checker.
     * @param  args args
     **/
    public void checkoutHelper(String[] args) {
        if (!_branches.containsKey(args[1])) {
            System.out.println("No such branch exists.");
            return;
        }
        if (_keyToCurrBranch.equals(args[1])) {
            System.out.println("No need to checkout the current branch.");
            return;
        }
        List<String> temp = Utils.plainFilenamesIn(".");
        ArrayList<String> allFiles = new ArrayList<>();
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).contains(".txt")) {
                allFiles.add(temp.get(i));
            }
        }
        Commit headCommit = _branches.get(args[1]).getLast();
        for (String s : allFiles) {
            if (!_stagedFiles.containsKey(s)
                    && !_head.getFiles().containsKey(s)
                    && (headCommit.getFiles().containsKey(s)
                    && !Utils.readContentsAsString(
                            new File(headCommit.getFiles().get(s)))
                    .equals(Utils.readContentsAsString(
                            new File(s))))) {
                System.out.println("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
                return;
            }
        }


        for (String s : headCommit.getFiles().keySet()) {
            File actualFile = new File(s);
            Utils.writeContents(actualFile, Utils.readContentsAsString(
                    new File(headCommit.getFiles().get(s))));
        }
        for (String s : _head.getFiles().keySet()) {
            if (!headCommit.getFiles().containsKey(s)) {
                File delete = new File(s);
                delete.delete();
            }
        }
        if (args[1] != _keyToCurrBranch) {
            for (String s : _stagedFiles.keySet()) {
                File delete = new File(".gitlet/staging/" + s);
                delete.delete();
            }
            _stagedFiles.clear();
            _removeFiles.clear();
        }
        _head = headCommit;
        _keyToCurrBranch = args[1];
    }
    /** Checks out files, branches or commit files.
     * @param  args args
     **/
    public void checkout(String[] args) throws IOException {
        if (args.length == 2) {
            checkoutHelper(args);
        } else if (args.length == 3) {
            if (!_head.getFiles().containsKey(args[2])) {
                System.out.println("File does not exist in that commit.");
                return;
            }
            File checkoutFile = new File(args[2]);
            Utils.writeContents(checkoutFile, Utils.readContentsAsString(
                    new File(_head.getFiles().get(args[2]))));
        } else if (args.length == 4) {
            if (!args[2].equals("--")) {
                System.out.println("Incorrect operands");
                return;
            }
            String arg = args[1];
            boolean checker2 = false;
            File checkoutFile = Utils.join(CWD, args[3]);
            if (args[1].length() == 8) {
                for (Commit c : _commits.values()) {
                    if (c.getSha1().substring(0, 8).equals(args[1])) {
                        arg = c.getSha1();
                        break;
                    }
                }
            }
            Commit pointer = _head;
            while (pointer != null) {
                if (pointer.getSha1().equals(arg)) {
                    checker2 = true;
                    break;
                } else {
                    pointer = pointer.getParent();
                }
            }
            if (!checker2) {
                System.out.println("No commit with that id exists.");
                return;
            }
            if (!pointer.getFiles().containsKey(args[3])) {
                System.out.println("File does not exist in that commit.");
                return;
            }
            String id = pointer.getFiles().get(args[3]);
            byte[] b = Utils.readContents(new File(id));
            Utils.writeContents(Utils.join(CWD, args[3]), b);
        }
    }
    /** Prints the files and details of commits .
     * @param  args args
     **/
    public void log(String[] args) throws IOException {
        Commit pointer = _head;
        while (pointer != null) {
            String time = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z")
                    .format(pointer.getDate());
            if (pointer.isMerge() && pointer.getMergeParents().size() == 2) {
                String temp = "===" + "\n" + "commit "
                        + pointer.getSha1() + "\n" + "Merge: "
                        + pointer.getMergeParents()
                        .getFirst().getSha1().substring(0, 7)
                        + " " + pointer.getMergeParents().getLast()
                        .getSha1().substring(0, 7)
                        + "\n" + "Date: " + time + "\n"
                        + pointer.getMsg() + "\n";
                System.out.println(temp);
                pointer = pointer.getMergeParents().getFirst();
            } else {
                String test = "===" + "\n" + "commit " + pointer.getSha1()
                        + "\n" + "Date: " + time + "\n" + pointer.getMsg()
                        + "\n";
                System.out.println(test);
                pointer = pointer.getParent();
            }
        }
    }
    /** Removes certain files.
     * @param  args args
     **/
    public void rm(String[] args) throws IOException {
        File removeFile = new File(args[1]);
        if (!_stagedFiles.containsKey(args[1])
                && !_head.getFiles().containsKey(args[1])) {
            System.out.println("No reason to remove the file.");
        }
        if (_stagedFiles.containsKey(args[1])) {
            _stagedFiles.remove(args[1]);
            File f = new File(".gitlet/staging/" + args[1]);
            f.delete();
        }
        if (_head.getFiles().containsKey(args[1])) {
            _removeFiles.add(args[1]);
            if (removeFile.exists()) {
                removeFile.delete();
            }
        }
    }
    /** Finds a commit.
     * @param  args args
     **/
    public void find(String[] args) {
        ArrayList<String> commitList = new ArrayList<>();
        boolean checker3 = true;
        for (Commit c : _commits.values()) {
            String msg = c.getMsg();
            if (msg.equals(args[1])) {
                commitList.add(c.getSha1());
            }
        }
        if (commitList.size() == 0) {
            System.out.println("Found no commit with that message");
            return;
        }
        for (int i = 0; i < commitList.size(); i++) {
            System.out.println(commitList.get(i));
        }
    }
    /** Creates a new branch and pointer.
     * @param  args args
     **/
    public void branch(String[] args) {
        if (_branches.containsKey(args[1])) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        LinkedList<Commit> commit = new LinkedList<>();
        Commit pointer = _head;
        commit.add(pointer);
        while (pointer != null) {
            pointer = pointer.getParent();
            commit.addFirst(pointer);
        }
        _branches.put(args[1], commit);
    }
    /** Prints the status of the repo.
     * @param  args args
     **/
    public void status(String[] args) {
        if (_commits == null) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
        ArrayList<String> sorted = new ArrayList<>(_branches.keySet());
        Collections.sort(sorted);
        System.out.println("=== Branches ===");
        for (String s : sorted) {
            if (s.equals(_keyToCurrBranch)) {
                System.out.println("*" + s);
            } else {
                System.out.println(s);
            }
        }
        System.out.println("\n" + "=== Staged Files ===");
        for (String s : _stagedFiles.keySet()) {
            System.out.println(s);
        }
        System.out.println("\n" + "=== Removed Files ===");
        for (String s : _removeFiles) {
            System.out.println(s);
        }
        System.out.println("\n"
                + "=== Modifications Not Staged For Commit ===");
        if (!_head.isMerge()) {
            HashMap<String, String> files = allModified();
            for (String name : files.keySet()) {
                System.out.println(name + "(" + files.get(name) + ")");
            }
        }
        System.out.println("\n" + "=== Untracked Files ===");
        ArrayList<String> allFiles = new ArrayList<>();
        List<String> temp = Utils.plainFilenamesIn(".");
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).contains(".txt")) {
                allFiles.add(temp.get(i));
            }
        }
        Commit commitHead2 = _head;
        List<String> files2 = new ArrayList<>();
        HashMap<String, String> blob2 = commitHead2.getFiles();
        for (String s : allFiles) {
            if (!blob2.containsKey(s) && !_stagedFiles.containsKey(s)) {
                files2.add(s);
            }
        }
        for (String s : files2) {
            System.out.println(s);
        }
        System.out.println();
    }
    /** Returns a file.
     * @return Hashmap
     **/
    public HashMap<String, String> allModified() {
        ArrayList<String> allFiles = new ArrayList<>();
        List<String> temp = Utils.plainFilenamesIn(".");
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).contains(".txt")) {
                allFiles.add(temp.get(i));
            }
        }
        Commit commitHead = _head;
        HashMap<String, String> files = new HashMap<>();
        HashMap<String, String> blob = commitHead.getFiles();
        for (String s : allFiles) {
            if (blob.containsKey(s)
                    && !Utils.readContentsAsString(
                            new File(commitHead.getFiles().get(s)))
                    .equals(Utils.readContentsAsString(
                            new File(s)))
                    && !_stagedFiles.containsKey(s)) {
                files.put(s, "modified");
            }
        }
        for (String s : blob.keySet()) {
            if (!allFiles.contains(s)
                    && !_stagedFiles.containsKey(s)) {
                files.put(s, "deleted");
            }
        }
        return files;
    }
    /** Removes a branch.
     * @param  args args
     **/
    public void rmBranch(String[] args) {
        if (!_branches.containsKey(args[1])) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (args[1].equals(_keyToCurrBranch)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        _branches.remove(args[1]);
    }
    /** Resets a commit with a given id.
     * @param  args argse
     **/
    public void reset(String[] args) {
        if (!_commits.containsKey(args[1])) {
            System.out.println("No commit with that id exists.");
            return;
        }
        Commit headCommit = _commits.get(args[1]);
        List<String> temp = Utils.plainFilenamesIn(".");
        ArrayList<String> allFiles = new ArrayList<>();
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).contains(".txt")) {
                allFiles.add(temp.get(i));
            }
        }
        for (String s : allFiles) {
            if (!_stagedFiles.containsKey(s)
                    && !_head.getFiles().containsKey(s)
                    && (headCommit.getFiles().containsKey(s)
                    && !Utils.readContentsAsString(
                            new File(headCommit.getFiles().get(s)))
                    .equals(Utils.readContentsAsString(new File(s))))) {
                System.out.println("There is an untracked file "
                        + "in the way; delete it, or add and commit it first.");
                return;
            }
        }
        _stagedFiles.clear();
        _removeFiles.clear();
        for (String s : headCommit.getFiles().keySet()) {
            if (!headCommit.getFiles().containsKey(s)) {
                File delete = new File(s);
                delete.delete();
            } else {
                File actualFile = new File(s);
                Utils.writeContents(actualFile, Utils.readContentsAsString(
                        new File(headCommit.getFiles().get(s))));
            }
        }
        _head = headCommit;
        LinkedList<Commit> savedList = new LinkedList<Commit>();
        for (String s : _branches.keySet()) {
            for (Commit traverse : _branches.get(s)) {
                if (traverse != null && traverse.getSha1().equals(args[1])) {
                    savedList.add(traverse);
                    _branches.replace(_keyToCurrBranch, savedList);
                    return;
                }
                savedList.add(traverse);
            }
            savedList.clear();
        }
    }
    /** A log of all commits.
     * @param  args args
     **/
    public void globalLog(String[] args) {
        for (Commit pointer : _commits.values()) {
            String time = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z")
                    .format(pointer.getDate());
            System.out.println("===" + "\n" + "commit " + pointer.getSha1()
                    + "\n" + "Date: " + time + "\n" + pointer.getMsg() + "\n");
        }
    }
    /** Merging two branches.
     * @param  args args
     **/
    public void merge(String[] args) throws IOException {
        if (!_branches.containsKey(args[1])) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (!_stagedFiles.isEmpty() || !_removeFiles.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        if (args[1].equals(_keyToCurrBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }
        if (untrackedFiles(args)) {
            return;
        }
        LinkedList<Commit> splitPointLinkedList = new LinkedList<Commit>();
        LinkedList<Commit> currentLinkedList = _branches.get(_keyToCurrBranch);
        LinkedList<Commit> givenLinkedList = _branches.get(args[1]);

        for (Commit c : currentLinkedList) {
            if (c == null || givenLinkedList.contains(c)) {
                splitPointLinkedList.addLast(c);
            } else {
                break;
            }
        }
        Commit splitPoint = splitPointLinkedList.getLast();
        Commit current = currentLinkedList.getLast();
        Commit given = givenLinkedList.getLast();
        if (args[1].equals("master")) {
            checkout(new String[]{"checkout", args[1]});
            System.out.println("Current branch fast-forwarded.");
            return;
        } else if (_branches.get(args[1]).getLast().getSha1()
                .equals(_branches.get(_keyToCurrBranch).getLast().getSha1())) {
            checkout(new String[]{"checkout", args[1]});
            System.out.println("Current branch fast-forwarded.");
            return;
        } else if (splitPoint.getSha1().equals(given.getSha1())) {
            System.out.println("Given branch is an ancestor"
                    + " of the current branch.");
            return;
        }
        mergeHelper(current, splitPoint, given);
        mergeHelper2(current, splitPoint, given);
        if (!checker) {
            createMergeCommit(args);
        } else {
            System.out.println("Encountered a merge conflict.");
            checker = false;
            _head = given;
            createMergeCommit(args);

        }
    }
    /** mergeConflict print.
     * @param current current
     * @param given given
     * @return String string
     **/
    public String mergeConflict(String current, String given) {
        String file = "<<<<<<< HEAD"
                + "\n"
                + current
                + "======="
                + "\n"
                + given
                + ">>>>>>>"
                + "\n";
        return file;
    }
    /** Creates a merge commit.
     * @param args args
     **/
    public void createMergeCommit(String[] args) throws IOException {
        HashMap<String, String> tempFile = new HashMap<>();
        tempFile.putAll(_branches.get(_keyToCurrBranch)
                .getLast().getFiles());
        for (String s : _branches.get(args[1])
                .getLast().getFiles().keySet()) {
            tempFile.put(s, _branches.get(args[1])
                    .getLast().getFiles().get(s));
        }
        LinkedList<Commit> mergeParents = new LinkedList<>();
        mergeParents.addFirst(_branches.get(_keyToCurrBranch).getLast());
        mergeParents.addLast(_branches.get(args[1]).getLast());
        Commit com = new Commit(new Date(), "Merged " + args[1]
                + " into " + _keyToCurrBranch
                + ".", tempFile, mergeParents, true);
        commitMerge(com);
    }
    /** Finds untracked files and prints warning.
     * @param args args
     * @return boolean
     **/
    public boolean untrackedFiles(String[] args) {
        List<String> temp = Utils.plainFilenamesIn(".");
        ArrayList<String> allFiles = new ArrayList<>();
        for (int i = 0; i < temp.size(); i++) {
            if (temp.get(i).contains(".txt")) {
                allFiles.add(temp.get(i));
            }
        }
        Commit headCommit = _branches.get(args[1]).getLast();
        for (String s : allFiles) {
            if (!_stagedFiles.containsKey(s)
                    && !_head.getFiles().containsKey(s)
                    && (headCommit.getFiles().containsKey(s)
                    && !Utils.readContentsAsString(
                            new File(headCommit.getFiles().get(s)))
                    .equals(Utils.readContentsAsString(new File(s))))) {
                System.out.println("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
                return true;
            }
        }
        return false;
    }
    /** Abstracts away some work of merge.
     * @param current current
     * @param splitPoint splitpoint
     * @param given given
     **/
    public void mergeHelper(Commit current, Commit splitPoint,
                            Commit given) throws IOException {
        for (String s : current.getFiles().keySet()) {
            if (splitPoint.getFiles().containsKey(s)
                    && given.getFiles().containsKey(s)) {
                if (!given.getFiles().get(s).equals(current.getFiles().get(s))
                        || !splitPoint.getFiles().get(s)
                        .equals(current.getFiles().get(s))
                        || !splitPoint.getFiles().get(s)
                        .equals(given.getFiles().get(s))) {
                    String change = mergeConflict(Utils.readContentsAsString(
                                    new File(".gitlet/blobs/"
                                            + Utils.sha1(
                                                    Utils.readContentsAsString(
                                            new File(
                                                    current.getFiles()
                                                            .get(s)))))) + "\n",
                            Utils.readContentsAsString(
                                    new File(".gitlet/blobs/"
                                            + Utils.sha1(
                                            Utils.readContentsAsString(
                                                    new File(given.getFiles()
                                                            .get(s))))))
                                    + "\n");
                    Utils.writeContents(new File(s), change);
                    checker = true;
                }
                if (!splitPoint.getFiles().get(s)
                        .equals(given.getFiles().get(s))
                        && splitPoint.getFiles().get(s)
                        .equals(current.getFiles()
                                .get(s))) {
                    checkout(new String[]{"checkout",
                            given.getSha1(), "--", s});
                    add(new String[]{"add", s});
                }
            } else if (!given.getFiles().containsKey(s)
                    && splitPoint.getFiles().containsKey(s)
                    && !splitPoint.getFiles().get(s)
                    .equals(current.getFiles().get(s))) {
                String change = mergeConflict(Utils.readContentsAsString(
                        new File(".gitlet/blobs/"
                                + Utils.sha1(Utils.readContentsAsString(
                                new File(current.getFiles().get(s))))))
                        + "\n", "");
                Utils.writeContents(new File(s), change);
                checker = true;
            }
        }
        for (String f : splitPoint.getFiles().keySet()) {
            if (current.getFiles().containsKey(f)) {
                if (splitPoint.getFiles().get(f)
                        .equals(current.getFiles().get(f))
                        && !given.getFiles().containsKey(f)) {
                    rm(new String[]{"remove", f});
                }
            }
        }
    }
    /** Abstracts away some work of merge.
     * @param current current
     * @param splitPoint splitpoint
     * @param given given
     **/
    public void mergeHelper2(Commit current, Commit splitPoint,
                             Commit given) throws IOException {
        for (String s : given.getFiles().keySet()) {
            if (!splitPoint.getFiles().containsKey(s)) {
                _head = given;
                checkout(new String[]{"checkout", given.getSha1(), "--", s});
                add(new String[]{"add", s});
            }
        }
        for (String c : current.getFiles().keySet()) {
            if (!splitPoint.getFiles().containsKey(c)
                    && !given.getFiles().containsKey(c)) {
                if (c.equals("f.txt")) {
                    new File(c).delete();
                }
            }
        }
    }
}
