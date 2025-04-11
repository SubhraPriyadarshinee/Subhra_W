package com.walmart.move.nim.receiving.rc.entity;

import java.util.Date;
import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "PRODUCT_CATEGORY_GROUP")
@Getter
@Setter
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryGroup {

  @EmbeddedId @EqualsAndHashCode.Include private ProductCategoryGroupPK id;

  @Column(name = "\"GROUP\"", length = 1, nullable = false)
  private String group;

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS", nullable = false, updatable = false)
  private Date createTs;

  @Column(name = "CREATE_USER", length = 50, nullable = false, updatable = false)
  private String createUser;

  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS", nullable = false)
  private Date lastChangedTs;

  @Column(name = "LAST_CHANGED_USER", length = 50, updatable = false)
  private String lastChangedUser;

  // TODO: How come none of our tables use version column?
  // @Expose
  // @NotNull
  // @Version
  // @Column(name = "VERSION")
  // private Integer version;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  public void onCreate() {
    this.createTs = new Date();
  }
}
