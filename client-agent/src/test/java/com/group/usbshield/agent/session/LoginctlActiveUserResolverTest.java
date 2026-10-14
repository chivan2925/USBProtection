package com.group.usbshield.agent.session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class LoginctlActiveUserResolverTest {
 static class Fake implements LoginctlGateway {
  final List<SessionProperties> sessions;
  Fake(SessionProperties...s){sessions=List.of(s);}
  public List<String> listSessionIds(){return sessions.stream().map(SessionProperties::sessionId).toList();}
  public SessionProperties showSession(String id){return sessions.stream().filter(x->x.sessionId().equals(id)).findFirst().orElseThrow();}
 }
 private SessionProperties session(String id,String name,String uid,String seat,String type) {return new SessionProperties(id,name,uid,"yes",seat,type,"no");}
 @Test void choosesUniqueLocalUser(){var result=new LoginctlActiveUserResolver(new Fake(session("21","usbdev","1000","seat0","wayland"))).resolveCurrentActiveUser();assertEquals("usbdev",result.username());assertEquals(1000L,result.uid());}
 @Test void choosesOnlyGraphicalSeat0(){var result=new LoginctlActiveUserResolver(new Fake(session("1","tty","1001","","tty"),session("2","student01","1002","seat0","x11"))).resolveCurrentActiveUser();assertEquals("student01",result.username());}
 @Test void rejectsAmbiguousSessions(){var result=new LoginctlActiveUserResolver(new Fake(session("1","a","1001","seat0","wayland"),session("2","b","1002","seat0","wayland"))).resolveCurrentActiveUser();assertEquals("UNKNOWN",result.username());assertEquals("ambiguous-active-local-sessions",result.reason());}
 @Test void rejectsNoLocalSession(){var result=new LoginctlActiveUserResolver(new Fake()).resolveCurrentActiveUser();assertEquals("UNKNOWN",result.status());}
 @Test void returnsUnknownOnUnexpectedRuntimeError(){var result=new LoginctlActiveUserResolver(new LoginctlGateway(){public List<String> listSessionIds(){throw new IllegalStateException("fail");}public SessionProperties showSession(String id){throw new AssertionError();}}).resolveCurrentActiveUser();assertEquals("loginctl-error",result.reason());}
}
