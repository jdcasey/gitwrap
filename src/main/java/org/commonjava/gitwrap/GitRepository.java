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

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.JGitInternalException;
import org.eclipse.jgit.api.NoFilepatternException;
import org.eclipse.jgit.api.NoHeadException;
import org.eclipse.jgit.api.NoMessageException;
import org.eclipse.jgit.api.WrongRepositoryStateException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.File;
import java.io.IOException;

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

}
