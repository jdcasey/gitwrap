/*
 *  Copyright (C) 2010 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.commonjava.gitwrap;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class BareGitRepositoryTest
{

    @Test
    public void cloneBare_NoBranch()
        throws IOException, GitWrapException, URISyntaxException
    {
        final File gitDir = new File( ".git" );
        if ( gitDir.exists() )
        {
            final FileRepositoryBuilder builder = new FileRepositoryBuilder();
            builder.setGitDir( gitDir );
            builder.setup();

            final FileRepository repository = new FileRepository( builder );
            final RemoteConfig config = new RemoteConfig( repository.getConfig(), "origin" );

            final File newGitDir = File.createTempFile( "git-clone.", ".git" );
            newGitDir.delete();

            BareGitRepository.setProgressMonitor( new TextProgressMonitor() );

            final BareGitRepository clone =
                BareGitRepository.clone( config.getURIs().get( 0 ).toString(), "origin", newGitDir, true );

            System.out.println( clone.getGitDir() );

            System.out.println();

            final FetchResult fetchResult = clone.getLatestFetchResult();
            for ( final Ref ref : fetchResult.getAdvertisedRefs() )
            {
                System.out.println( ref.getName() );
            }

            System.out.println();

            for ( final TrackingRefUpdate update : fetchResult.getTrackingRefUpdates() )
            {
                System.out.println( update.getLocalName() + " -> " + update.getRemoteName() );
            }
        }
    }

    @Test
    public void cloneNonBare_NoBranch()
        throws IOException, GitWrapException, URISyntaxException
    {
        final File gitDir = new File( ".git" );
        if ( gitDir.exists() )
        {
            final FileRepositoryBuilder builder = new FileRepositoryBuilder();
            builder.setGitDir( gitDir );
            builder.setup();

            final FileRepository repository = new FileRepository( builder );
            final RemoteConfig config = new RemoteConfig( repository.getConfig(), "origin" );

            final File newWorkDir = File.createTempFile( "git-clone", "" );
            newWorkDir.delete();

            final File newGitDir = new File( newWorkDir, ".git" );

            BareGitRepository.setProgressMonitor( new TextProgressMonitor() );

            final BareGitRepository clone =
                BareGitRepository.clone( config.getURIs().get( 0 ).toString(), "origin", newGitDir, false );

            System.out.println( clone.getGitDir() );

            System.out.println();

            final FetchResult fetchResult = clone.getLatestFetchResult();
            for ( final Ref ref : fetchResult.getAdvertisedRefs() )
            {
                System.out.println( ref.getName() );
            }

            System.out.println();

            for ( final TrackingRefUpdate update : fetchResult.getTrackingRefUpdates() )
            {
                System.out.println( update.getLocalName() + " -> " + update.getRemoteName() );
            }
        }
    }

}
