package org.apache.olingo.jpa.processor.core.testmodel;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.SecondaryTable;
import javax.persistence.Table;

import org.apache.olingo.jpa.metadata.core.edm.NamingStrategy;
import org.apache.olingo.jpa.metadata.core.edm.entity.ODataEntity;

@Entity(name = "EntityWithSecondaryTableAndEmbedded")
@Table(schema = "\"OLINGO\"", name = "\"org.apache.olingo.jpa::BusinessPartner\"")
//Eclipselink+Hibernate cannot handle qualified table names for @SecondaryTable
@SecondaryTable(schema = "\"OLINGO\"", name = "SECONDARYTABLEEXAMPLEWITHSIMPLENAME",
pkJoinColumns = {
    @PrimaryKeyJoinColumn(name = "\"ID\"") })
@ODataEntity(edmEntitySetName = "EntityWithSecondaryTableAndEmbeddedSet", attributeNaming = NamingStrategy.AsIs)
public class EntityWithSecondaryTableAndEmbedded {
  @Id
  @Column(name = "\"ID\"")
  protected String ID;

  @Column(name = "\"NameLine1\"")
  private String firstName;

  @Column(name = "\"NameLine2\"")
  private String lastName;

  // single attribute targeting another table
  @Column(table = "SECONDARYTABLEEXAMPLEWITHSIMPLENAME", name = "\"DATA\"")
  private String data;

  // use @Embedded + @AttributeOverride targeting values in another table
  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "created.by", column = @Column(
        table = "SECONDARYTABLEEXAMPLEWITHSIMPLENAME",
        name = "\"CreatedBy\"", insertable = false, updatable = false)),
    @AttributeOverride(name = "created.at", column = @Column(
        table = "SECONDARYTABLEEXAMPLEWITHSIMPLENAME",
        name = "\"CreatedAt\"", insertable = false, updatable = false)),
    @AttributeOverride(name = "updated.by", column = @Column(
        table = "SECONDARYTABLEEXAMPLEWITHSIMPLENAME",
        name = "\"UpdatedBy\"", insertable = false, updatable = false)),
    @AttributeOverride(name = "updated.at", column = @Column(
        table = "SECONDARYTABLEEXAMPLEWITHSIMPLENAME",
        name = "\"UpdatedAt\"", insertable = false, updatable = false)) })
  private final AdministrativeInformationSearchable editInformation = new AdministrativeInformationSearchable();


}
