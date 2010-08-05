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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.Tag;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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

public class BareGitRepository
{

    private static final Logger LOGGER = Logger.getLogger( BareGitRepository.class );

    private final File gitDir;

    private final File workDir;

    private final FileRepository repository;

    private final Git git;

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
            repository.create( workDir == null );
        }

        git = new Git( repository );
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

            final ProgressMonitor monitor = NullProgressMonitor.INSTANCE;
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
                    final PushResult result = transport.push( monitor, updates );

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
