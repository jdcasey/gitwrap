/*
 * Copyright (c) 2010 Red Hat, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see 
 * <http://www.gnu.org/licenses>.
 */

package org.commonjava.gitwrap;

import org.eclipse.jgit.lib.Constants;

public final class GitPathBuilder
    implements CharSequence
{

    public StringBuilder builder = new StringBuilder();

    public static GitPathBuilder createPath()
    {
        return new GitPathBuilder();
    }

    public GitPathBuilder withRemoteRef( final String remoteName )
    {
        builder.append( Constants.R_REMOTES ).append( remoteName );
        return this;
    }

    public GitPathBuilder withHeadRef( final String head )
    {
        builder.append( Constants.R_HEADS ).append( head );
        return this;
    }

    public GitPathBuilder withTagRef( final String tag )
    {
        builder.append( Constants.R_TAGS ).append( tag );
        return this;
    }

    public GitPathBuilder named( final String name )
    {
        if ( builder.length() > 0 && builder.charAt( builder.length() - 1 ) != '/' )
        {
            builder.append( '/' );
        }

        builder.append( name );

        return this;
    }

    @Override
    public String toString()
    {
        return builder.toString();
    }

    @Override
    public int length()
    {
        return builder.length();
    }

    @Override
    public char charAt( final int index )
    {
        return builder.charAt( index );
    }

    @Override
    public CharSequence subSequence( final int start, final int end )
    {
        return builder.subSequence( start, end );
    }
}
