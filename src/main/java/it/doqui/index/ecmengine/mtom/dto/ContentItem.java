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
 * DTO astratto superclasse di tutti i DTO dotati di prefixed name.
 * </p>
 * <p>
 * Il prefixed name &egrave; una stringa composta da un prefisso e un nome,
 * separati da due punti; ad esempio, nel prefixed name
 * {@code app:company_home}, {@code app} &egrave; il prefisso e
 * {@code company_home} &egrave; il nome.
 * </p>
 * <p>
 * Un prefisso &egrave; definito nei content model (sia di base che quelli
 * custom) ed &egrave; associato univocamente a un URI; ad esempio, il prefisso
 * di base {@code app} &egrave; associato all'URI
 * {@code http://www.alfresco.org/model/application/1.0}.
 * </p>
 * <p>
 * I prefixed name sono utilizzati per nominare tipi, propriet&agrave; aspetti e
 * associazioni.
 * </p>
 * 
 * @author DoQui
 */
public abstract class ContentItem extends MtomEngineDto {
    private static final long serialVersionUID = 4944002745232278225L;

    /**
     * Separatore presente tra prefisso e nome.
     */
    public static final char PREFIXED_NAME_SEPARATOR = ':';

    private String prefixedName;

    /**
     * Restituisce il prefixed name.
     */
    public final String getPrefixedName() {
	return this.prefixedName;
    }

    /**
     * Imposta il prefixed name.
     */
    public final void setPrefixedName(String prefixedName) {
	this.prefixedName = prefixedName;
    }
}
