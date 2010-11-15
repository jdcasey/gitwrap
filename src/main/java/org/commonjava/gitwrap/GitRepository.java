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

    public static GitRepository clone( final String remoteUrl, final String remoteName, final File targetDir,
                                       final boolean bare )
        throws GitWrapException
    {
        return clone( remoteUrl, remoteName, null, targetDir, bare );
    }

    public static GitRepository clone( final String remoteUrl, final String remoteName, final String branch,
                                       final File targetDir, final boolean bare )
        throws GitWrapException
    {
        File gitDir = targetDir;
        if ( !Constants.DOT_GIT.equals( targetDir.getName() ) )
        {
            gitDir = new File( targetDir, Constants.DOT_GIT );
        }

        return (GitRepository) BareGitRepository.clone( remoteUrl, remoteName, branch, gitDir, bare );
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

        checkoutBranch( source, name );

        return this;
    }

    public GitRepository checkoutBranch( final String source, final String name )
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
            final FileRepository repository = getRepository();

            try
            {
                final RevWalk walk = new RevWalk( repository );

                final RevCommit newCommit = walk.parseCommit( repository.resolve( refName ) );
                final RevCommit oldCommit = walk.parseCommit( repository.resolve( source ) );

                final GitIndex index = repository.getIndex();
                final RevTree newTree = newCommit.getTree();
                final RevTree oldTree = oldCommit.getTree();
                final WorkDirCheckout checkout =
                    new WorkDirCheckout( repository, repository.getWorkTree(), repository.mapTree( oldTree ), index,
                                         repository.mapTree( newTree ) );

                checkout.checkout();

                index.write();

                final RefUpdate u = repository.updateRef( source, false );
                u.setRefLogMessage( "checkout: moving to " + refName, false );
                final Result res = u.link( refName );

                switch ( res )
                {
                    case NEW:
                    case FORCED:
                    case NO_CHANGE:
                    case FAST_FORWARD:
                        break;
                    default:
                        throw new GitWrapException( "Error linking branch: %s to source: %s after checkout. %s",
                                                    refName, source, u.getResult().name() );
                }
            }
            catch ( final IOException e )
            {
                throw new GitWrapException( "Failed to checkout branch: %s from: %s.\nReason: %s", e, refName, source,
                                            e.getMessage() );
            }
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
