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

package it.doqui.index.ecmengine.mtom.dto;

/**
 * DTO contenente parametri aggiuntivi per i servizi massivi e semi massivi.
 * 
 * @author DoQui
 */
public class MassiveParameter extends MtomEngineDto {
    private static final long serialVersionUID = 2634507442770008073L;

    public MassiveParameter() {
	super();
    }

    public MassiveParameter(boolean oldImplementation, boolean synchronousReindex) {
	super();
	this.oldImplementation = oldImplementation;
	this.synchronousReindex = synchronousReindex;
    }

    private boolean oldImplementation;
    private boolean synchronousReindex;

    /**
     * Indica se deve essere utilizzata la vecchia implementazione del servizio semi
     * massiva o la nuova massiva.
     */
    public boolean isOldImplementation() {
	return oldImplementation;
    }

    /**
     * Imposta se deve essere utilizzata la vecchia implementazione del servizio
     * semi massiva o la nuova massiva.
     */
    public void setOldImplementation(boolean oldImplementation) {
	this.oldImplementation = oldImplementation;
    }

    /**
     * Indica se deve essere eseguita una indicizzazione sincrona con l'esecuzione
     * del servizio o se verr&agrave; eseguita con una richiesta successiva.
     */
    public boolean isSynchronousReindex() {
	return synchronousReindex;
    }

    /**
     * Imposta se deve essere eseguita una indicizzazione sincrona con l'esecuzione
     * del servizio o se verr&agrave; eseguita con una richiesta successiva.
     * 
     * @param synchronousReindex
     */
    public void setSynchronousReindex(boolean synchronousReindex) {
	this.synchronousReindex = synchronousReindex;
    }
}
