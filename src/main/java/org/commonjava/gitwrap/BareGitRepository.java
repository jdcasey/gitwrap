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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.Tag;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.lib.WorkDirCheckout;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings( "deprecation" )
public class BareGitRepository
{

    private static final Logger LOGGER = Logger.getLogger( BareGitRepository.class );

    private static ProgressMonitor MONITOR = NullProgressMonitor.INSTANCE;

    private final File gitDir;

    private final File workDir;

    private final FileRepository repository;

    private final Git git;

    private FetchResult latestFetch;

    public BareGitRepository( final File gitDir )
        throws IOException
    {
        this( gitDir, true, null );
    }

    public BareGitRepository( final File gitDir, final boolean create )
        throws IOException
    {
        this( gitDir, create, null );
    }

    protected BareGitRepository( final File gitDir, final boolean create, final File workDir )
        throws IOException
    {
        this.gitDir = gitDir;
        this.workDir = workDir;

        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir( gitDir );
        if ( workDir != null )
        {
            builder.setWorkTree( workDir );
        }

        builder.setup();

        repository = new FileRepository( builder );

        if ( create && !gitDir.exists() )
        {
            final File objectsDir = new File( gitDir, "objects" );
            final File refsDir = new File( gitDir, "refs" );

            refsDir.mkdirs();
            objectsDir.mkdirs();

            repository.create( workDir == null );
            final FileBasedConfig config = repository.getConfig();

            config.setInt( "core", null, "repositoryformatversion", 0 );
            config.setBoolean( "core", null, "filemode", true );
            config.setBoolean( "core", null, "bare", workDir == null );
            config.setBoolean( "core", null, "logallrefupdates", true );
            config.setBoolean( "core", null, "ignorecase", true );

            config.save();
        }

        git = new Git( repository );
    }

    public static void setProgressMonitor( final ProgressMonitor monitor )
    {
        MONITOR = monitor;
    }

    public static BareGitRepository clone( final String remoteUrl, final String remoteName, final File gitDir,
                                           final boolean bare )
        throws GitWrapException
    {
        return clone( remoteUrl, remoteName, null, gitDir, bare );
    }

    public static BareGitRepository clone( final String remoteUrl, final String remoteName, final String branch,
                                           final File gitDir, final boolean bare )
        throws GitWrapException
    {
        final File workDir = gitDir.getParentFile();

        BareGitRepository gitRepository;
        try
        {
            gitRepository = bare ? new BareGitRepository( gitDir, true ) : new GitRepository( workDir, true );
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Cannot initialize new Git repository in: %s. Reason: %s", e, bare ? gitDir
                            : workDir, e.getMessage() );
        }

        final FileRepository repository = gitRepository.getRepository();

        final String branchRef = Constants.R_HEADS + ( branch == null ? Constants.MASTER : branch );

        try
        {
            final RefUpdate head = repository.updateRef( Constants.HEAD );
            head.disableRefLog();
            head.link( branchRef );

            final RemoteConfig remoteConfig = new RemoteConfig( repository.getConfig(), remoteName );
            remoteConfig.addURI( new URIish( remoteUrl ) );

            final String remoteRef = Constants.R_REMOTES + remoteName;

            RefSpec spec = new RefSpec();
            spec = spec.setForceUpdate( true );
            spec = spec.setSourceDestination( Constants.R_HEADS + "*", remoteRef + "/*" );

            remoteConfig.addFetchRefSpec( spec );

            remoteConfig.update( repository.getConfig() );

            repository.getConfig().setString( "branch", branch, "remote", remoteName );
            repository.getConfig().setString( "branch", branch, "merge", branchRef );

            repository.getConfig().save();

            gitRepository.fetch( remoteName );
            gitRepository.postClone( remoteUrl, branchRef );
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to clone from: %s. Reason: %s", e, remoteUrl, e.getMessage() );
        }
        catch ( final URISyntaxException e )
        {
            throw new GitWrapException( "Failed to clone from: %s. Reason: %s", e, remoteUrl, e.getMessage() );
        }

        return gitRepository;
    }

    protected void postClone( final String remoteUrl, final String branchRef )
        throws GitWrapException
    {
        try
        {
            final FetchResult fetchResult = getLatestFetchResult();

            final Ref remoteHead = fetchResult.getAdvertisedRef( branchRef );
            if ( remoteHead != null && remoteHead.getObjectId() != null )
            {
                final Commit mapCommit = repository.mapCommit( remoteHead.getObjectId() );
                final RefUpdate u;

                u = repository.updateRef( Constants.HEAD );
                u.setNewObjectId( mapCommit.getCommitId() );
                u.forceUpdate();
            }
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to clone from: %s. Reason: %s", e, remoteUrl, e.getMessage() );
        }
    }

    public BareGitRepository fetch( final String remoteName )
        throws GitWrapException
    {
        Transport transport = null;
        try
        {
            final RemoteConfig remoteConfig = new RemoteConfig( repository.getConfig(), remoteName );
            if ( remoteConfig.getURIs() == null || remoteConfig.getURIs().isEmpty() )
            {
                throw new GitWrapException( "Remote: %s has no associated URLs.", remoteName );
            }

            if ( remoteConfig.getFetchRefSpecs() == null || remoteConfig.getFetchRefSpecs().isEmpty() )
            {
                throw new GitWrapException( "Remote: %s has no associated fetch ref-specs.", remoteName );
            }

            transport = Transport.open( repository, remoteConfig );
            latestFetch = transport.fetch( MONITOR, null );
        }
        catch ( final URISyntaxException e )
        {
            throw new GitWrapException( "Cannot read configuration for remote: %s. Reason: %s", e, remoteName,
                                        e.getMessage() );
        }
        catch ( final NotSupportedException e )
        {
            throw new GitWrapException( "Transport not supported for remote: %s. Error was: %s", e, remoteName,
                                        e.getMessage() );
        }
        catch ( final TransportException e )
        {
            throw new GitWrapException( "Transport error while fetching remote: %s. Error was: %s", e, remoteName,
                                        e.getMessage() );
        }
        finally
        {
            if ( transport != null )
            {
                transport.close();
            }
        }

        return this;
    }

    public FetchResult getLatestFetchResult()
    {
        return latestFetch;
    }

    public BareGitRepository push( final String name )
        throws GitWrapException
    {
        try
        {
            final StoredConfig config = repository.getConfig();
            final RemoteConfig remote = new RemoteConfig( config, name );

            final List<URIish> pushURIs = remote.getPushURIs();
            final List<RefSpec> pushRefSpecs = remote.getPushRefSpecs();

            final Collection<RemoteRefUpdate> remoteRefUpdates =
                Transport.findRemoteRefUpdatesFor( repository, pushRefSpecs, null );

            for ( final URIish uri : pushURIs )
            {
                final Collection<RemoteRefUpdate> updates = new ArrayList<RemoteRefUpdate>();
                for ( final RemoteRefUpdate rru : remoteRefUpdates )
                {
                    updates.add( new RemoteRefUpdate( rru, null ) );
                }

                Transport transport = null;
                try
                {
                    transport = Transport.open( repository, uri );
                    transport.applyConfig( remote );
                    final PushResult result = transport.push( MONITOR, updates );

                    if ( result.getMessages().length() > 0 && LOGGER.isDebugEnabled() )
                    {
                        LOGGER.debug( result.getMessages() );
                    }
                }
                finally
                {
                    if ( transport != null )
                    {
                        transport.close();
                    }
                }
            }
        }
        catch ( final NotSupportedException e )
        {
            throw new GitWrapException( "Cannot push to repository: %s. Transport is not supported.\nNested error: %s",
                                        e, name, e.getMessage() );
        }
        catch ( final TransportException e )
        {
            throw new GitWrapException( "Transport failed for repository push: %s.\nNested error: %s", e, name,
                                        e.getMessage() );
        }
        catch ( final URISyntaxException e )
        {
            throw new GitWrapException( "Invalid URI for repository push: %s.\nNested error: %s", e, name,
                                        e.getMessage() );
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Transport failed for repository push: %s.\nNested error: %s", e, name,
                                        e.getMessage() );
        }

        return this;
    }

    public BareGitRepository setPushTarget( final String name, final String uri, final boolean heads, final boolean tags )
        throws GitWrapException
    {
        try
        {
            final RemoteConfig remote = new RemoteConfig( repository.getConfig(), name );

            final URIish uriish = new URIish( uri );

            final List<URIish> uris = remote.getURIs();
            if ( uris == null || !uris.contains( uriish ) )
            {
                remote.addURI( uriish );
            }

            final List<URIish> pushURIs = remote.getPushURIs();
            if ( pushURIs == null || !pushURIs.contains( uriish ) )
            {
                remote.addPushURI( uriish );
            }

            final List<RefSpec> pushRefSpecs = remote.getPushRefSpecs();
            if ( heads )
            {
                final RefSpec headSpec = new RefSpec( "+" + Constants.R_HEADS + "*:" + Constants.R_HEADS + "*" );
                if ( pushRefSpecs == null || !pushRefSpecs.contains( headSpec ) )
                {
                    remote.addPushRefSpec( headSpec );
                }
            }

            if ( tags )
            {
                final RefSpec tagSpec = new RefSpec( "+" + Constants.R_TAGS + "*:" + Constants.R_TAGS + "*" );
                if ( pushRefSpecs == null || !pushRefSpecs.contains( tagSpec ) )
                {
                    remote.addPushRefSpec( tagSpec );
                }
            }

            remote.update( repository.getConfig() );
            repository.getConfig().save();
        }
        catch ( final URISyntaxException e )
        {
            throw new GitWrapException( "Invalid URI-ish: %s. Nested error: %s", e, uri, e.getMessage() );
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to write Git config: %s", e, e.getMessage() );
        }

        return this;
    }

    public BareGitRepository createTagFromHead( final String tagName, final String message )
        throws GitWrapException
    {
        return createTag( Constants.HEAD, tagName, message );
    }

    public BareGitRepository createTag( final String tagSource, final String tagName, final String message )
        throws GitWrapException
    {
        try
        {
            final ObjectId head = repository.resolve( tagSource );

            final Tag tag = new Tag( repository );
            tag.setTag( tagName );
            tag.setTagger( new PersonIdent( repository ) );
            tag.setObjId( head );
            tag.setMessage( message );

            final ObjectLoader object = repository.open( head );
            tag.setType( Constants.typeString( object.getType() ) );

            final ObjectInserter inserter = repository.newObjectInserter();
            final ObjectId tagId = inserter.insert( Constants.OBJ_TAG, inserter.format( tag ) );

            tag.setTagId( tagId );

            final String refName = Constants.R_TAGS + tag.getTag();

            final RefUpdate tagRef = repository.updateRef( refName );
            tagRef.setNewObjectId( tag.getTagId() );

            tagRef.setForceUpdate( true );
            final Result updateResult = tagRef.update();

            if ( updateResult != Result.NEW && updateResult != Result.FORCED )
            {
                throw new GitWrapException( "Invalid tag result: %s", updateResult );
            }
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to add tag: %s", e, e.getMessage() );
        }

        return this;
    }

    public String getHeadRevision()
        throws GitWrapException
    {
        try
        {
            return repository.resolve( Constants.HEAD ).getName();
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to retrieve HEAD revision. Reason: %s", e, e.getMessage() );
        }
    }

    public BareGitRepository createBranchFromHead( final String name )
        throws GitWrapException
    {
        return createBranch( Constants.HEAD, name );
    }

    public BareGitRepository createBranch( final String source, final String name )
        throws GitWrapException
    {
        final String refName = name.startsWith( Constants.R_HEADS ) ? name : Constants.R_HEADS + name;

        try
        {
            final RevWalk walk = new RevWalk( repository );

            final RefUpdate updateRef = repository.updateRef( refName );
            final ObjectId startAt = new RevWalk( repository ).parseCommit( repository.resolve( source ) );

            updateRef.setNewObjectId( startAt );
            updateRef.setRefLogMessage( "branch: Created from " + source, false );
            updateRef.update();

            final RevCommit newCommit = walk.parseCommit( repository.resolve( refName ) );
            final RevCommit oldCommit = walk.parseCommit( repository.resolve( source ) );

            final GitIndex index = repository.getIndex();
            final Tree newTree = newCommit.asCommit( walk ).getTree();
            final Tree oldTree = oldCommit.asCommit( walk ).getTree();
            final WorkDirCheckout checkout =
                new WorkDirCheckout( repository, repository.getWorkTree(), oldTree, index, newTree );

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
                    throw new GitWrapException( "Error linking new branch: %s to source: %s. %s", refName, source,
                                                u.getResult().name() );
            }
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to create branch: %s from: %s.\nReason: %s", e, refName, source,
                                        e.getMessage() );
        }

        return this;
    }

    public boolean hasBranch( final String name )
        throws GitWrapException
    {
        final String refName = Constants.R_HEADS + name;
        try
        {
            return repository.resolve( refName ) != null;
        }
        catch ( final IOException e )
        {
            throw new GitWrapException( "Failed to resolve branch: %s.\nReason: %s", e, refName, e.getMessage() );
        }

    }

    protected final Git getGit()
    {
        return git;
    }

    protected final FileRepository getRepository()
    {
        return repository;
    }

    public final File getGitDir()
    {
        return gitDir;
    }

    public final File getWorkDir()
    {
        return workDir;
    }

}
