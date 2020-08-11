package com.capitalone.dashboard.model;

import java.util.List;

public class LicensePolicyType {
   String policyName;
   List<String> descriptions;

   public String getPolicyName() {
      return policyName;
   }

   public void setPolicyName(String policyName) {
      this.policyName = policyName;
   }

   public List<String> getDescriptions() {
      return descriptions;
   }

   public void setDescriptions(List<String> descriptions) {
      this.descriptions = descriptions;
   }
}
