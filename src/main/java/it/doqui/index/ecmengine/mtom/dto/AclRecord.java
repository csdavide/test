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
 * <p>
 * DTO che rappresenta una ACL.
 * </p>
 * <p>
 * Una ACL pemette di definire quale permesso &egrave; concesso o negato a una
 * specifica autority.
 * </p>
 * <p>
 * Un'autority pu&ograve; essere un utente o un gruppo del tenant.
 * </p>
 * <p>
 * I permessi possono essere:
 * </p>
 * <ul>
 * <li><b>Consumer</b>: lettura.</li>
 * <li><b>Editor</b>: lettura e modifica.</li>
 * <li><b>Cotnributor</b>: lettura e creazione.</li>
 * <li><b>Collaborator</b>: lettura, modifica e creazione.</li>
 * </ul>
 * <p>
 * <b>Si consiglia di utilizzare solo ACL permissive, in quanto il comportamento
 * di default &egrave; remissivo (se non ci sono ACL impostate, l'accesso viene
 * negato).</b>
 * </p>
 * <p>
 * Si ricorda che sulla company home del tenant {@code /app:company_home} �
 * definita una ACL ereditata permissiva di tipo
 * {@code GROUP_EVERYONE - Consumer}, che permette di default a tutti gli utenti
 * di leggere tutto il contenuto del tenant: se questo non &egrave; il
 * comportamento desiderato, &egrave; possibile rimuovere l'ereditariet� della
 * ACL con il servizio
 * {@link EcmEngineBackofficeBusinessInterface#setInheritsAcl(Node, boolean, OperationContext)
 * setInheritsAcl}.
 * </p>
 * <p>
 * <b>Le operazioni eseguite con l'utente {@code admin} del tenant non sono
 * sottoposte ad alcuna ACL.</b>
 * </p>
 * 
 * @author DoQui
 */
public class AclRecord extends MtomEngineDto {
    private static final long serialVersionUID = 3797726773747698759L;
    private String authority;
    private String permission;
    private boolean accessAllowed;

    /**
     * Restituisce l'autority.
     */
    public String getAuthority() {
	return authority;
    }

    /**
     * Imposta l'autority.
     */
    public void setAuthority(String authority) {
	this.authority = authority;
    }

    /**
     * Restituisce il permesso.
     */
    public String getPermission() {
	return permission;
    }

    /**
     * Imposta il permesso.
     */
    public void setPermission(String permission) {
	this.permission = permission;
    }

    /**
     * Retituisce il comportamento permissivo o remissivo dell'ACL.
     */
    public boolean isAccessAllowed() {
	return accessAllowed;
    }

    /**
     * Imposta il comportamento permissivo o remissivo dell'ACL.
     */
    public void setAccessAllowed(boolean allowed) {
	this.accessAllowed = allowed;
    }
}
