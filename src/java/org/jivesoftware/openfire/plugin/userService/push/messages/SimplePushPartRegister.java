package org.jivesoftware.openfire.plugin.userService.push.messages;

import java.util.*;

/**
 * Holds mapping Action -> List<SimplePushPart> and merges with another register.
 *
 * Created by dusanklinec on 12.03.15.
 */
public class SimplePushPartRegister {
    private PartRegister register;

    public SimplePushPartRegister() {

    }

    private SimplePushPartRegister(PartRegister register) {
        this.register = register;
    }

    /**
     * Builds object from the push message.
     * @param msg
     * @return
     */
    public static SimplePushPartRegister buildFrom(SimplePushMessage msg){
        return buildFrom(msg.getParts());
    }

    /**
     * Builds object from list of the parts.
     * @param parts
     * @return
     */
    public static SimplePushPartRegister buildFrom(List<SimplePushPart> parts){
        PartRegister register = new PartRegister();
        for (SimplePushPart part : parts) {
            final String action = part.getAction();

            List<SimplePushPart> lst = register.get(action);
            if (lst == null){
                lst = new ArrayList<SimplePushPart>();
            }

            lst.add(part);
            register.put(action, lst);
        }

        return new SimplePushPartRegister(register);
    }

    /**
     * Merge this push message register with provided one.
     * Returns true if merge has changed this register.
     *
     * @param reg
     * @return
     */
    public boolean mergeWithRegister(SimplePushPartRegister reg){
        boolean wasModified = false;

        // Build part list.
        PartRegister myRegister = register;
        PartRegister foRegister = reg.getRegister();

        // Iterate my register and add his stuff, if has newer. Delete if present so we can then add all rest.
        Set<String> keys = new HashSet<String>(myRegister.keySet());
        for (String key : keys) {
            final List<SimplePushPart> lst = myRegister.get(key);
            if (lst == null){
                continue;
            }

            // Does this entry exist in ofRegister? if not, continue with merging on different field.
            List<SimplePushPart> foLst = foRegister.get(key);
            if (foLst == null || foLst.isEmpty()){
                foRegister.remove(key);
                continue;
            }

            // Add all elements since our is empty.
            if (lst.isEmpty()){
                lst.addAll(foLst);
                foRegister.remove(key);
                wasModified |= true;
                continue;
            }

            // Our list is non-empty, his as well.
            SimplePushPart myPart = lst.get(0);
            SimplePushPart foPart = foLst.get(0);

            // Can merge be done on these?
            if (myPart.isUnique()){
                // Let message parts do the join.
                boolean partChanged = myPart.mergeWith(foPart);
                if (partChanged){
                    lst.clear();
                    lst.add(foPart);
                    wasModified |= true;
                }
            } else {
                // Union parts, joint them.
                lst.addAll(foLst);
                foRegister.remove(key);
                wasModified |= true;
            }
        }

        // Add all items that left in given message.
        if (!foRegister.isEmpty()) {
            myRegister.putAll(foRegister);
            wasModified |= true;
        }

        return wasModified;
    }

    /**
     * Build new part from the updated register.
     * @return
     */
    public void buildParts(List<SimplePushPart> parts){
        parts.clear();
        for (Map.Entry<String, List<SimplePushPart>> e : register.entrySet()) {
            for (SimplePushPart part : e.getValue()) {
                parts.add(part);
            }
        }
    }

    public PartRegister getRegister() {
        return register;
    }

    public void setRegister(PartRegister register) {
        this.register = register;
    }

    public static class PartRegister extends HashMap<String, List<SimplePushPart>> {
        // ...
    }
}
