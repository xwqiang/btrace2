/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://www.opensolaris.org/os/licensing.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 *
 * ident	"%Z%%M%	%I%	%E% SMI"
 */
package org.opensolaris.os.dtrace;

import java.io.*;
import java.util.EventObject;

/**
 * Notification that the state of a target process designated by {@link
 * Consumer#createProcess(String command)} or {@link
 * Consumer#grabProcess(int pid)} has changed.
 *
 * @see ConsumerListener#processStateChanged(ProcessEvent e)
 *
 * @author Tom Erickson
 */
public class ProcessEvent extends EventObject {
    static final long serialVersionUID = -3779443761929558702L;

    /**
     * Creates a {@link ConsumerListener#processStateChanged(ProcessEvent e)
     * processStateChanged()} event to notify listeners of a process
     * state change.
     *
     * @param source the {@link Consumer} that is the source of this event
     * @throws NullPointerException if the given process state is {@code
     * null}
     */
    public
    ProcessEvent(Object source, ProcessState p)
    {
	super(source);
    }

    /**
     * Gets the process state.
     *
     * @return non-null process state
     */
    public ProcessState
    getProcessState()
    {
	return null;
    }
}