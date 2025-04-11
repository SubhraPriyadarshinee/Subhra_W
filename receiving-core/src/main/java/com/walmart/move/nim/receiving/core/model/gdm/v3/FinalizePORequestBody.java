package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class FinalizePORequestBody {

  private Date finalizedTime;
  private List<FinalizePOLine> lines;
  private int rcvdQty;
  private String rcvdQtyUom;
  private FinalizePOOSDRInfo damage;
  private FinalizePOOSDRInfo overage;
  private FinalizePOOSDRInfo reject;
  private FinalizePOOSDRInfo shortage;
  private String userId;
  @NotNull private FinalizePOReasonCode reasonCode;

  private transient int totalBolFbq = 0;

  public void addRcvdQty(int rcvdQty) {
    this.rcvdQty += rcvdQty;
  }

  public void addDamageQty(int damageQty) {
    if (Objects.isNull(damage)) {
      damage = new FinalizePOOSDRInfo();
      damage.setQuantity(damageQty);
      damage.setUom(ReceivingConstants.Uom.VNPK);
    } else {
      damage.setQuantity(damage.getQuantity() + damageQty);
    }
  }

  public void addOverageQty(int overageQty) {
    if (Objects.isNull(overage)) {
      overage = new FinalizePOOSDRInfo();
      overage.setQuantity(overageQty);
      overage.setUom(ReceivingConstants.Uom.VNPK);
    } else {
      overage.setQuantity(overage.getQuantity() + overageQty);
    }
  }

  public void addRejectQty(int rejectQty) {
    if (Objects.isNull(reject)) {
      reject = new FinalizePOOSDRInfo();
      reject.setQuantity(rejectQty);
      reject.setUom(ReceivingConstants.Uom.VNPK);
    } else {
      reject.setQuantity(reject.getQuantity() + rejectQty);
    }
  }

  public void addShortageQty(int shortQty) {
    if (Objects.isNull(shortage)) {
      shortage = new FinalizePOOSDRInfo();
      shortage.setQuantity(shortQty);
      shortage.setUom(ReceivingConstants.Uom.VNPK);
    } else {
      shortage.setQuantity(shortage.getQuantity() + shortQty);
    }
  }
}
