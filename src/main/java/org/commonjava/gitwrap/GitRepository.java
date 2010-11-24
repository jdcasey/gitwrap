/*
 *  Copyright (c) 2010 Red Hat, Inc.
 *  
 *  This program is licensed to you under Version 3 only of the GNU
 *  General Public License as published by the Free Software 
 *  Foundation. This program is distributed in the hope that it will be 
 *  useful, but WITHOUT ANY WARRANTY; without even the implied 
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR 
 *  PURPOSE.
 *  
 *  See the GNU General Public License Version 3 for more details.
 *  You should have received a copy of the GNU General Public License 
 *  Version 3 along with this program. 
 *  
 *  If not, see http://www.gnu.org/licenses/.
 */

package org.commonjava.gitwrap;

import org.apache.log4j.Logger;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.lib.WorkDirCheckout;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.File;
import java.io.IOException;

@SuppressWarnings( "deprecation" )
public class GitRepository
    extends BareGitRepository
{

    private static final Logger LOGGER = Logger.getLogger( GitRepository.class );

    public GitRepository( final File workDir )
        throws IOException
    {
        this( workDir, true );
    }

    public GitRepository( final File workDir, final boolean create )
        throws IOException
    {
        super( new File( workDir, Constants.DOT_GIT ), create, workDir );
    }

    public static GitRepository cloneWithWorkdir( final String remoteUrl, final String remoteName,
                                                  final File targetDir, final boolean bare )
        throws GitWrapException
    {
        return cloneWithWorkdir( remoteUrl, remoteName, null, targetDir, bare );
    }

    public static GitRepository cloneWithWorkdir( final String remoteUrl, final String remoteName, final String branch,
                                                  final File targetDir, final boolean bare )
        throws GitWrapException
    {
        File workDir = targetDir;
        if ( workDir.getName().equals( ".git" ) )
        {
            workDir = workDir.getParentFile();
        }

        GitRepository repo;
        try
        {
            repo = new GitRepository( workDir, true );
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Cannot initialize new Git repository in: %s. Reason: %s", e, targetDir,
                                        e.getMessage() );
        }

        repo.doClone( remoteUrl, remoteName, branch );

        return repo;
    }

    public GitRepository commitChanges( final String message, final String... filePatterns )
        throws GitWrapException
    {
        final AddCommand add = getGit().add();
        add.setWorkingTreeIterator( new FileTreeIterator( getRepository() ) );
        for ( final String pattern : filePatterns )
        {
            add.addFilepattern( pattern );
        }

        try
        {
            add.call();
        }
        catch ( final NoFilepatternException e )
        {
            throw new GitWrapException( "Failed to add file patterns: %s", e, e.getMessage() );
        }

        try
        {
            getGit().commit().setMessage( message ).setAuthor( new PersonIdent( getRepository() ) ).call();
        }
        catch ( final NoHeadException e )
        {
            throw new GitWrapException( "Commit failed: no repository HEAD. Nested error: %s", e, e.getMessage() );
        }
        catch ( final NoMessageException e )
        {
            throw new GitWrapException( "Commit failed: no message provided. Nested error: %s", e, e.getMessage() );
        }
        catch ( final UnmergedPathException e )
        {
            throw new GitWrapException( "Commit failed: you have unmerged paths. Nested error: %s", e, e.getMessage() );
        }
        catch ( final ConcurrentRefUpdateException e )
        {
            throw new GitWrapException( "Commit failed: %s", e, e.getMessage() );
        }
        catch ( final JGitInternalException e )
        {
            throw new GitWrapException( "Commit failed: %s", e, e.getMessage() );
        }
        catch ( final WrongRepositoryStateException e )
        {
            throw new GitWrapException( "Commit failed: %s", e, e.getMessage() );
        }

        return this;
    }

    @Override
    public GitRepository createBranch( final String source, final String name )
        throws GitWrapException
    {
        super.createBranch( source, name );

        checkoutBranch( name );

        return this;
    }

    public GitRepository checkoutBranch( final String name )
        throws GitWrapException
    {
        final String refName;
        if ( name == null )
        {
            refName = Constants.HEAD;
        }
        else if ( name.startsWith( Constants.R_HEADS ) || name.startsWith( Constants.R_TAGS ) )
        {
            refName = name;
        }
        else
        {
            refName = Constants.R_HEADS + name;
        }

        if ( hasBranch( refName ) )
        {
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.debug( "Checking out: " + refName );
            }

            final FileRepository repository = getRepository();
            final boolean detach = !refName.startsWith( Constants.R_HEADS );

            try
            {
                final RevWalk walk = new RevWalk( repository );
                final RevCommit newCommit = walk.parseCommit( repository.resolve( refName ) );
                final RevCommit oldCommit = walk.parseCommit( repository.resolve( Constants.HEAD ) );
                final GitIndex index = repository.getIndex();
                final RevTree newTree = newCommit.getTree();
                final RevTree oldTree = oldCommit.getTree();

                if ( LOGGER.isDebugEnabled() )
                {
                    LOGGER.debug( "Checking out: " + newCommit + " (resolved from: " + refName + ")" );
                }

                final WorkDirCheckout checkout =
                    new WorkDirCheckout( repository, repository.getWorkTree(), repository.mapTree( oldTree ), index,
                                         repository.mapTree( newTree ) );

                checkout.checkout();

                if ( LOGGER.isDebugEnabled() )
                {
                    LOGGER.debug( "Writing index..." );
                }

                index.write();

                final RefUpdate u = repository.updateRef( Constants.HEAD, detach );
                Result result;
                if ( detach )
                {
                    u.setNewObjectId( newCommit.getId() );
                    u.setRefLogMessage( "Switching to detached branch: " + refName, false );

                    if ( LOGGER.isDebugEnabled() )
                    {
                        LOGGER.debug( "Updating head ref to point to: " + newCommit.getId() );
                    }

                    result = u.forceUpdate();
                }
                else
                {
                    u.setRefLogMessage( "Switching to linked branch: " + refName, false );

                    if ( LOGGER.isDebugEnabled() )
                    {
                        LOGGER.debug( "Linking head ref to: " + refName );
                    }

                    result = u.link( refName );
                }

                switch ( result )
                {
                    case NEW:
                    case FORCED:
                    case NO_CHANGE:
                    case FAST_FORWARD:
                        break;
                    default:
                        throw new GitWrapException( "Error checking out branch: %s. Result: %s", refName, u.getResult()
                                                                                                           .name() );
                }
            }
            catch ( final IOException e )
            {
                throw new GitWrapException( "Failed to checkout branch: %s.\nReason: %s", e, refName, e.getMessage() );
            }
        }
        else
        {
            throw new GitWrapException(
                                        "Cannot checkout non-existent branch: %s. Perhaps you meant to call createBranch(..)?",
                                        refName );
        }

        return this;
    }

    @Override
    protected void postClone( final String remoteUrl, final String branchRef )
        throws GitWrapException
    {
        super.postClone( remoteUrl, branchRef );

        final FetchResult fetchResult = getLatestFetchResult();

        try
        {
            final Ref remoteHead = fetchResult.getAdvertisedRef( branchRef );
            if ( remoteHead != null && remoteHead.getObjectId() != null )
            {
                final FileRepository repo = getRepository();

                final RevWalk walk = new RevWalk( repo );
                final RevCommit commit = walk.parseCommit( remoteHead.getObjectId() );
                final GitIndex index = new GitIndex( repo );
                final Tree tree = repo.mapTree( commit.getTree() );

                new WorkDirCheckout( repo, repo.getWorkTree(), index, tree ).checkout();
                index.write();
            }
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to checkout: %s after cloning from: %s.\nReason: %s", e, branchRef,
                                        remoteUrl, e.getMessage() );
        }
        finally
        {
        }
    }

}
