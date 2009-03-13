package ai;

import org.jdyna.IPlayerController;
import org.jdyna.IPlayerFactory;

public class MyPlayerFactory implements IPlayerFactory
{
    @Override
    public IPlayerController getController(String playerName)
    {
        return new MyPlayer(playerName);
    }
    
    @Override
    public String getDefaultPlayerName()
    {
        return "DauTrz";
    }
    
    @Override
    public String getVendorName()
    {
        return "daukszewicz-trzaszczka";
    }
}