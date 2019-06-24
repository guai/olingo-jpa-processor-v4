package org.apache.olingo.jpa.processor.core.testmodel.dataaccessconditioner;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.apache.olingo.jpa.metadata.core.edm.entity.ODataEntity;
import org.apache.olingo.jpa.processor.core.testmodel.CommunicationData;

@Entity
@ODataEntity(handlerDataAccessConditioner = BusinessPartnerDataAccessConditioner.class)
@Table(schema = "\"OLINGO\"", name = "\"org.apache.olingo.jpa::BusinessPartner\"")
public class GenericBusinessPartner {

	@Id
	@Column(name = "\"ID\"")
	protected String ID;

	@Column(name = "\"Type\"", length = 1, nullable = false, insertable = false, updatable = false)
	protected String type;

	@Column(name = "\"Country\"", length = 4)
	private String country;

	// Embedded type should never be null
	@Embedded
	protected CommunicationData communicationData = new CommunicationData();

	@OneToOne(mappedBy = "businessPartnerPerson")
	private BusinessPartnerImage image;

}
