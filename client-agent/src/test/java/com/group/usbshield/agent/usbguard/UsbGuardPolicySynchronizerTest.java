package com.group.usbshield.agent.usbguard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class UsbGuardPolicySynchronizerTest {
 static class FakeRunner extends UsbGuardCommandRunner {
  final List<String> rules=new ArrayList<>(List.of("1: allow with-interface none-of { 08:*:* }"));int next=2,added=0,removed=0;boolean deny=false;
  @Override public CommandResult run(String...argv) {
   if(deny && !"list-rules".equals(argv[0]))return new CommandResult(1,"IPC DENIED");
   return switch(argv[0]) {
    case "list-rules" -> new CommandResult(0,String.join("\n",rules));
    case "get-parameter" -> new CommandResult(0,"block");
    case "append-rule" -> {rules.add((next++)+": "+argv[1]);added++;yield new CommandResult(0,"ok");}
    case "remove-rule" -> {rules.removeIf(s->s.startsWith(argv[1]+":"));removed++;yield new CommandResult(0,"ok");}
    default -> new CommandResult(1,"unsupported");
   };
  }
 }
 private final DeviceFingerprint kingston=new DeviceFingerprint("0951","1665","SERIAL123","hash=");
 @Test void allowsThenRevokesIdempotentlyAndPreservesBase(){
  FakeRunner r=new FakeRunner();var sync=new UsbGuardPolicySynchronizer(r,new UsbGuardOutputParser(),new UsbGuardRuleBuilder());
  sync.reconcile(List.of());sync.reconcile(List.of(kingston));sync.reconcile(List.of(kingston));
  assertEquals(1,r.added);assertTrue(r.rules.getFirst().contains("none-of"));
  sync.reconcile(List.of());sync.reconcile(List.of());assertEquals(1,r.removed);assertEquals(1,r.rules.size());
 }
 @Test void rejectsMissingBase(){
  FakeRunner r=new FakeRunner();r.rules.clear();
  assertThrows(IllegalStateException.class,()->new UsbGuardPolicySynchronizer(r,new UsbGuardOutputParser(),new UsbGuardRuleBuilder()).reconcile(List.of(kingston)));
 }
 @Test void failsOnIpcDenied(){
  FakeRunner r=new FakeRunner();r.deny=true;
  assertThrows(IllegalStateException.class,()->new UsbGuardPolicySynchronizer(r,new UsbGuardOutputParser(),new UsbGuardRuleBuilder()).reconcile(List.of(kingston)));
 }
 @Test void preservesForeignRules(){
  FakeRunner r=new FakeRunner();r.rules.add("2: allow id 1234:abcd serial \"EXTERNAL\"");
  var sync=new UsbGuardPolicySynchronizer(r,new UsbGuardOutputParser(),new UsbGuardRuleBuilder());
  sync.reconcile(List.of());assertEquals(2,r.rules.size());assertEquals(0,r.removed);
 }
 @Test void disallowsUnsafeFingerprint(){
  assertThrows(IllegalArgumentException.class,()->new UsbGuardRuleBuilder().allowRule(new DeviceFingerprint("0951","1665","bad\";rm",null)));
 }
}
