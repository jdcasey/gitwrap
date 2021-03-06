# Gitwrap - Convenience API for jGit #

Gitwrap is designed to eliminate the need to understand and manipulate the myriad nuts-and-bolts of jGit directly.

## Usage ##

Let's say you have a directory structure like this:

    project/
    |
    +- pom.xml
    +- src/
       |
       +- main/
          |
          +- java/
             |
             +- Main.java

If you want to initialize a new Git repository for this project, commit all files, 
and then push the whole thing out to a GitHub repository, you'd use something like this:

    GitRepository repository = new GitRepository( project.getBasedir() ); # init automatically, if necessary

    repository.commitChanges( "Initial commit", "." )     # commitChanges( commitMsg, pattern... )
              .setPushTarget( "origin", url, true, true ) # setPushTarget( remoteName, remoteUrl, pushHeads, pushTags )
              .push( "origin" );                          # push( remoteName )


