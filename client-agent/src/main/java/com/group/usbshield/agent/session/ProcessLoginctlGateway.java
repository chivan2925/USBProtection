package com.group.usbshield.agent.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Member 2 — Week 2: secure argv-based loginctl with bounded execution. */
public class ProcessLoginctlGateway implements LoginctlGateway {
    public List<String> listSessionIds() {
        return run("list-sessions", "--no-legend", "--no-pager").lines().map(String::trim)
                .filter(s->!s.isBlank()).map(s->s.split("\\s+")[0]).toList();
    }
    public SessionProperties showSession(String sid) {
        if (sid == null || !sid.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid sessionId");
        String output = run("show-session", sid, "-p", "Name", "-p", "User", "-p", "Active", "-p", "Remote", "-p", "Seat", "-p", "Type", "--no-pager");
        Map<String,String> props = new HashMap<>();
        output.lines().filter(s->s.contains("=")).forEach(line->{int i=line.indexOf('=');props.put(line.substring(0,i),line.substring(i+1));});
        return new SessionProperties(sid, props.getOrDefault("Name",""), props.getOrDefault("User",""),
                props.getOrDefault("Active",""), props.getOrDefault("Seat",""),
                props.getOrDefault("Type",""), props.getOrDefault("Remote",""));
    }
    private String run(String... args) {
        List<String> cmd = new ArrayList<>();cmd.add("loginctl");cmd.addAll(List.of(args));
        Process process=null; ExecutorService reader=Executors.newSingleThreadExecutor();
        try {
            process=new ProcessBuilder(cmd).redirectErrorStream(true).start();
            Process live=process;
            Future<String> output=reader.submit(()->new String(live.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
            if (!process.waitFor(5,TimeUnit.SECONDS)) {process.destroyForcibly();throw new IllegalStateException("loginctl timed out");}
            String text=output.get(2,TimeUnit.SECONDS);
            if (process.exitValue()!=0) throw new IllegalStateException("loginctl failed ("+process.exitValue()+")");
            return text;
        } catch (IOException e) {throw new IllegalStateException("Cannot execute loginctl",e);}
        catch (InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("loginctl interrupted",e);}
        catch (ExecutionException|TimeoutException e) {throw new IllegalStateException("Cannot read loginctl output",e);}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();reader.shutdownNow();}
    }
}
