package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "LABEL_META_DATA")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LabelMetaData extends BaseMTEntity {

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "labelMetaData_Sequence")
  @SequenceGenerator(
      name = "labelMetaData_Sequence",
      sequenceName = "labelMetaData_Sequence",
      allocationSize = 1)
  @Expose(serialize = false, deserialize = false)
  private Long id;

  @Expose
  @Column(name = "LABEL_ID", nullable = false)
  private int labelId;

  @Expose
  @Column(name = "LABEL_NAME")
  @Enumerated(EnumType.STRING)
  private LabelFormatId labelName;

  @Expose
  @Column(name = "DESCRIPTION")
  private String description;

  @Expose
  @Column(name = "LPAAS_FORMAT_NAME", nullable = false)
  private String lpaasFormatName;

  @Expose
  @Column(name = "JSON_TEMPLATE", nullable = false)
  private String jsonTemplate;

  @Expose
  @Column(name = "ZPL")
  private String zpl;

  @Expose
  @Column(name = "MPCL_F")
  private String mpclF;

  @Expose
  @Column(name = "MPCL_D")
  private String mpclD;
}
