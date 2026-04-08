/* Index ECM Engine - A system for managing the capture (when created 
 * or received), classification (cataloguing), storage, retrieval, 
 * revision, sharing, reuse and disposition of documents.
 *
 * Copyright (C) 2008 Regione Piemonte
 * Copyright (C) 2008 Provincia di Torino
 * Copyright (C) 2008 Comune di Torino
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package it.doqui.index.ecmengine.mtom.exception;

import it.doqui.index.ecmengine.business.foundation.util.FoundationErrorCodes;

/**
 * Eccezione generica dei servizi interni.
 * 
 * @author DoQui
 */
public class EcmEngineFoundationException extends Exception {
    protected FoundationErrorCodes code;

    /**
     * Costruttore predefinito.
     */
    protected EcmEngineFoundationException() {
	super();
	this.code = FoundationErrorCodes.UNKNOWN_ERROR;
    }

    /**
     * Costruttore che prende in input un codice di errore.
     */
    protected EcmEngineFoundationException(FoundationErrorCodes errorCode) {
	super();
	this.code = errorCode;
    }

    /**
     * Costruttore che prende in input il messaggio dell'eccezione.
     */
    public EcmEngineFoundationException(String msg) {
	super(msg);
	this.code = FoundationErrorCodes.UNKNOWN_ERROR;
    }

    /**
     * Costruttore che prende in input il messaggio dell'eccezione e la sua causa.
     */
    public EcmEngineFoundationException(String msg, Throwable cause) {
	super(msg, cause);
	this.code = FoundationErrorCodes.UNKNOWN_ERROR;
    }

    /**
     * Restituisce il codice di errore.
     */
    public final FoundationErrorCodes getCode() {
	return this.code;
    }
}
