package org.kivpn.vpn;

public class FakeAdminCheck extends AdminCheck {

    private boolean admin = true;

    @Override
    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
