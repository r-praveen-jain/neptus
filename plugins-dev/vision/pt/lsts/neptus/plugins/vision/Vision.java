/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Pedro Gonçalves
 * Apr 4, 2015
 */
package pt.lsts.neptus.plugins.vision;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import pt.lsts.imc.Announce;
import pt.lsts.imc.CcuEvent;
import pt.lsts.imc.EstimatedState;
import pt.lsts.imc.MapFeature;
import pt.lsts.imc.MapPoint;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.ConfigurationListener;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.renderer2d.LayerPriority;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.map.AbstractElement;
import pt.lsts.neptus.types.map.MapGroup;
import pt.lsts.neptus.types.map.MapType;
import pt.lsts.neptus.types.map.MarkElement;
import pt.lsts.neptus.types.mission.MapMission;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.util.GuiUtils;

import com.google.common.eventbus.Subscribe;

/**
 * Neptus Plugin for Video Stream and tag frame/object
 * 
 * @author pedrog
 * @version 1.1
 * @category Vision
 *
 */
@SuppressWarnings("serial")
@Popup( pos = POSITION.RIGHT, width=660, height=500)
@LayerPriority(priority=0)
@PluginDescription(name="Video Stream", version="1.1", author="Pedro Gonçalves", description="Plugin for View video Stream TCP-Ip/Ip-Cam", icon="pt/lsts/neptus/plugins/ipcam/camera.png")
public class Vision extends ConsolePanel implements ConfigurationListener, ItemListener{

    private static final String BASE_FOLDER_FOR_IMAGES = "log/images";
    private static final String BASE_FOLDER_FOR_ICON_IMAGES = "plugins-dev/vision/iconImages";

    @NeptusProperty(name = "Axis Camera RTPS URI")
    private String camRtpsUrl = "rtsp://10.0.20.207:554/live/ch01_0";//"rtsp://10.0.20.102:554/axis-media/media.amp?streamprofile=Mobile";

    private ServerSocket serverSocket = null;
    //Send data for sync 
    private PrintWriter out = null; 
    //Buffer for data image
    private InputStream is = null;
    //Buffer for info of data image
    private BufferedReader in = null;
    //Struct Video Capture Opencv
    private VideoCapture capture;
    //Width size of image
    private int widthImgRec;
    //Height size of image
    private int heightImgRec;
    //Width size of Console
    private int widhtConsole = 640;
    //Height size of Console
    private int heightConsole = 480;
    //Scale factor of x pixel
    private float xScale;
    //Scale factor of y pixel
    private float yScale;
    //x pixel coord
    private int xPixel;
    //y pixel coord
    private int yPixel;
    //read size of pack compress
    private String line;
    //Buffer for data receive from DUNE over tcp
    private String duneGps;
    //Size of image received
    private int lengthImage;
    //buffer for save data receive
    private byte[] data;
    //Buffer image for JFrame/showImage
    private BufferedImage temp;
    //Flag - start acquired image
    private boolean raspiCam = false;
    //Flag - Lost connection to the vehicle
    private boolean state = false;
    //Flag - Show/hide Menu JFrame
    private boolean show_menu = false;
    //Flag state of IP CAM
    private boolean ipCam = false;
    //Save image tag flag
    private boolean captureFrame = false;
    //Close comTCP state
    private boolean closeComState = false;
    
    private boolean closingPanel = false;
    
    //JLabel for image
    private JLabel picLabel;
    //JPanel for display image
    private JPanel panelImage;
    //JPanel for info and config values
    private JPanel config;
    //JText info of data receive
    private JTextField txtText;
    //JText of data receive over IMC message
    private JTextField txtData;
    //JText of data receive over DUNE TCP message
    private JTextField txtDataTcp;
    //JFrame for menu options
    private JFrame menu;
    //CheckBox to save image to HD
    private JCheckBox saveToDiskCheckBox;
    //JPopup Menu
    private JPopupMenu popup;
    
    //String for the info treatment 
    private String info;
    //String for the info of Image Size Stream
    private String infoSizeStream;
    //Data system
    private Date date = new Date();
    //Location of log folder
    private String logDir;
    //Image resize
    private Mat matResize;
    //Image receive
    private Mat mat;
    //Size of output frame
    private Size size = null;
    //Counter for image tag
    private int cntTag = 1;

    //counter for frame tag ID
    private short frameTagID =1;
    //lat, lon: frame Tag pos to be marked as POI
    private double lat,lon;

    //*** TEST FOR SAVE VIDEO **/
    private File outputfile;
    private boolean flagBuffImg = false;
    private int cnt = 0;
    private int FPS = 10;
    //*************************/
    
    //worker thread designed to acquire the data packet from DUNE
    private Thread updater = null; 
    //worker thread designed to save image do HD
    private Thread saveImg = null;
    
    public Vision(ConsoleLayout console) {
        super(console);
        //clears all the unused initializations of the standard ConsolePanel
        removeAll();
        //Resize Console
        this.addComponentListener(new ComponentAdapter() {  
            public void componentResized(ComponentEvent evt) {
                Component c = evt.getComponent();
                widhtConsole = c.getSize().width;
                heightConsole = c.getSize().height;
                widhtConsole = widhtConsole - 22;
                heightConsole = heightConsole - 22;          
                xScale = (float)widhtConsole/widthImgRec;
                yScale = (float)heightConsole/heightImgRec;
                size = new Size(widhtConsole, heightConsole);
                matResize = new Mat(heightConsole, widhtConsole, CvType.CV_8UC3);
                if(!raspiCam && !ipCam)
                    inicImage();
            }
        });
        //Mouse click
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1){
                    if(raspiCam || ipCam){
                        xPixel = (int) ((e.getX() - 11) / xScale);  //shift window bar
                        yPixel = (int) ((e.getY() - 11) / yScale) ; //shift window bar
                        if(raspiCam && !ipCam) {
                            if (xPixel >= 0 && yPixel >= 0 && xPixel <= widthImgRec && yPixel <= heightImgRec)
                                out.printf("%d#%d;\0", xPixel,yPixel);
                        }
                       // System.out.println(getMainVehicleId()+"X = " +e.getX()+ " Y = " +e.getY());
                        captureFrame = true;
                        //place mark on map as POI
                        placeLocationOnMap();
                    }
                }
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popup = new JPopupMenu();
                    @SuppressWarnings("unused")
                    JMenuItem item1;
                    popup.add(item1 = new JMenuItem("Start RasPiCam", new ImageIcon(String.format(BASE_FOLDER_FOR_ICON_IMAGES + "/raspicam.jpg")))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            if(!ipCam){
                                raspiCam = true;
                                ipCam = false;
                                closeComState = false;
                            }
                            else{
                                NeptusLog.pub().info("Clossing IpCam Stream...");
                                closeComState = false;
                                raspiCam = true;
                                state = false;
                                ipCam = false;
                            }  
                        }
                    });
                    @SuppressWarnings("unused")
                    JMenuItem item2;
                    popup.add(item2 = new JMenuItem("Close all connection", new ImageIcon(String.format(BASE_FOLDER_FOR_ICON_IMAGES + "/close.gif")))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            NeptusLog.pub().info("Clossing all Video Stream...");
                            raspiCam = false;
                            state = false;
                            ipCam = false;
                        }
                    });
                    @SuppressWarnings("unused")
                    JMenuItem item3;
                    popup.add(item3 = new JMenuItem("Start Ip-Cam", new ImageIcon(String.format(BASE_FOLDER_FOR_ICON_IMAGES + "/ipcam.png")))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            if(!raspiCam){
                                ipCam = true;
                                raspiCam = false;
                                state = false;
                            }
                            else{
                                NeptusLog.pub().info("Clossing RasPiCam Stream...");
                                ipCam = true;
                                raspiCam = false;
                                state = false;
                                closeComState = true;
                            }
                        }
                    });
                    @SuppressWarnings("unused")
                    JMenuItem item4;
                    popup.add(item4 = new JMenuItem("Config", new ImageIcon(String.format(BASE_FOLDER_FOR_ICON_IMAGES + "/config.jpeg")))).addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            //show_menu = !show_menu;
                            menu.setVisible(true);
                        }
                    });
                    popup.show((Component) e.getSource(), e.getX(), e.getY());
                }
            }
        });
        return;
    }

    public String timestampToReadableHoursString(long timestamp){
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(date);
    }

    /**
     * Adapted from ContactMarker.placeLocationOnMap()
     */
    private void placeLocationOnMap() {

        if (getConsole().getMission() == null)
            return;

        double lat = this.lat;
        double lon = this.lon;
        long timestamp = System.currentTimeMillis();
        String id = I18n.text("FrameTag") + "-" + frameTagID + "-" + timestampToReadableHoursString(timestamp);

        boolean validId = false;
        while (!validId) {
            id = JOptionPane.showInputDialog(getConsole(), I18n.text("Please enter new mark name"), id);
            if (id == null)
                return;
            AbstractElement elems[] = MapGroup.getMapGroupInstance(getConsole().getMission()).getMapObjectsByID(id);
            if (elems.length > 0)
                GuiUtils.errorMessage(getConsole(), I18n.text("Add mark"),
                        I18n.text("The given ID already exists in the map. Please choose a different one"));
            else
                validId = true;
        }
        frameTagID++;//increment ID

        MissionType mission = getConsole().getMission();
        LinkedHashMap<String, MapMission> mapList = mission.getMapsList();
        if (mapList == null)
            return;
        if (mapList.size() == 0)
            return;
        // MapMission mapMission = mapList.values().iterator().next();
        MapGroup.resetMissionInstance(getConsole().getMission());
        MapType mapType = MapGroup.getMapGroupInstance(getConsole().getMission()).getMaps()[0];// mapMission.getMap();
        // NeptusLog.pub().info("<###>MARKER --------------- " + mapType.getId());
        MarkElement contact = new MarkElement(mapType.getMapGroup(), mapType);

        contact.setId(id);
        contact.setCenterLocation(new LocationType(lat,lon));
        mapType.addObject(contact);
        mission.save(false);
        MapPoint point = new MapPoint();
        point.setLat(lat);
        point.setLon(lon);
        point.setAlt(0);
        MapFeature feature = new MapFeature();
        feature.setFeatureType(MapFeature.FEATURE_TYPE.POI);
        feature.setFeature(Arrays.asList(point));
        CcuEvent event = new CcuEvent();
        event.setType(CcuEvent.TYPE.MAP_FEATURE_ADDED);
        event.setId(id);
        event.setArg(feature);
        this.getConsole().getImcMsgManager().broadcastToCCUs(event);
        NeptusLog.pub().info("placeLocationOnMap: " + id + " - Pos: lat: " + this.lat + " ; lon: " + this.lon);
    }

    //!Print Image to JPanel
    private void showImage(BufferedImage image) {
        picLabel.setIcon(new ImageIcon(image));
        panelImage.revalidate();
        panelImage.add(picLabel, BorderLayout.CENTER);
        repaint();
    }

    //!Config Layout
    private void configLayout() {
        //Create Buffer (type MAT) for Image resize
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        matResize = new Mat(heightConsole, widhtConsole, CvType.CV_8UC3);
        
        //!Create folder to save image data
        //Create folder image in log if don't exist
        File dir = new File(String.format(BASE_FOLDER_FOR_IMAGES));
        dir.mkdir();
        //Create folder Image to save data received
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s", date));
        dir.mkdir();
        //Create folder Image Tag
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s/imageTag", date));
        dir.mkdir();
        //Create folder Image Save
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s/imageSave", date));
        dir.mkdir();
        logDir = String.format(BASE_FOLDER_FOR_IMAGES + "/%s", date);
        
        //JLabel for image
        picLabel = new JLabel();
        //JPanel for Image
        panelImage = new JPanel();
        panelImage.setBackground(Color.LIGHT_GRAY);
        panelImage.setSize(this.getWidth(), this.getHeight());
        
        this.setLayout(new MigLayout());
        this.add(panelImage, BorderLayout.CENTER);
        
        //JPanel for info and config values      
        config = new JPanel(new MigLayout());
        
        //JCheckBox save to hd
        saveToDiskCheckBox = new JCheckBox(I18n.text("Save Image to Disk"));
        saveToDiskCheckBox.setMnemonic(KeyEvent.VK_C);
        saveToDiskCheckBox.setSelected(false);
        saveToDiskCheckBox.addItemListener(this);
        config.add(saveToDiskCheckBox,"width 160:180:200, h 40!, wrap");
        
        //JText info Data received
        txtText = new JTextField();
        txtText.setEditable(false);
        txtText.setToolTipText(I18n.text("Info of Frame Received"));
        info = String.format("X = 0 - Y = 0   x 1   0 bytes (KiB = 0)\t\t  ");
        txtText.setText(info);
        config.add(txtText, "cell 0 4 3 1, wrap");
        
        //JText info Data GPS received TCP
        txtDataTcp = new JTextField();
        txtDataTcp.setEditable(false);
        txtDataTcp.setToolTipText(I18n.text("Info of GPS Received"));
        info = String.format("\t\t\t\t  ");
        txtDataTcp.setText(info);
        config.add(txtDataTcp, "cell 0 5 3 1, wrap");
        
        //JText info
        txtData = new JTextField();
        txtData.setEditable(false);
        txtData.setToolTipText(I18n.text("Info of Frame Received"));
        info = String.format("\t\t\t\t  ");
        txtData.setText(info);
        config.add(txtData, "cell 0 6 3 1, wrap");
                
        menu = new JFrame(I18n.text("Menu Config"));
        menu.setVisible(show_menu);
        menu.setResizable(false);
        menu.setSize(450, 350);
        ImageIcon imgMenu = new ImageIcon("plugins-dev/vision/iconImages/config.jpeg");
        menu.setIconImage(imgMenu.getImage());
        menu.add(config);
    }
    
    /* (non-Javadoc)
     * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
     */
    @Override
    public void itemStateChanged(ItemEvent e) {
        //checkbox listener
        Object source = e.getItemSelectable();
        //System.out.println("source: "+source);
        if (source == saveToDiskCheckBox) {
            //System.out.println("raspiCam="+raspiCam+"ipCam"+ipCam+"saveToDiskCheckBox"+saveToDiskCheckBox.isSelected());
            if ((raspiCam == true || ipCam == true) && saveToDiskCheckBox.isSelected() == true) {
                flagBuffImg = true;
                //System.out.println("Valor: "+flagBuffImg);
            }
            if ((raspiCam == false && ipCam == false) || saveToDiskCheckBox.isSelected() == false) {
                flagBuffImg=false;
            }
        }
    }
    
    /* (non-Javadoc)
     * @see pt.lsts.neptus.plugins.ConfigurationListener#propertiesChanged()
     */
    @Override
    public void propertiesChanged() {
    }
    
    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        closingPanel = true;
    }
    
    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
        getConsole().getImcMsgManager().addListener(this);
        configLayout();
        updater = updaterThread();
        updater.start();
        saveImg = updaterThreadSave();
        saveImg.start();
    }
    
    //Get size of image
    private void initSizeImage(){
        //Width size of image
        try {
            widthImgRec = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
        //Height size of image
        try {
            heightImgRec = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        xScale = (float) widhtConsole / widthImgRec;
        yScale = (float) heightConsole / heightImgRec;
        //Create Buffer (type MAT) for Image receive
        mat = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
    }
    
    //!Thread to handle data receive
    private Thread updaterThread() {
        Thread ret = new Thread("Video Stream Thread") {
            @Override
            public void run() {
                inicImage();
                while(true){
                    if (closingPanel) {
                        raspiCam = false;
                        state = false;
                        ipCam = false;
                    }
                    
                    if (raspiCam && !ipCam ) {
                        if (state == false){
                            //connection
                            tcpConnection();
                            //receive info of image size
                            initSizeImage();
                            state = true;
                        }
                        //receive data image
                        if(!closeComState)
                            receivedDataImage();
                        else
                            closeTcpCom();
                        if(!raspiCam && !state)
                            closeTcpCom();
                    }
                    else if (!raspiCam && ipCam) {
                        if (state == false){
                            //Create Buffer (type MAT) for Image receive
                            mat = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
                            state = true;
                            capture = new VideoCapture(camRtpsUrl);
                            cntTag = 1;
                            if (capture.isOpened())
                                NeptusLog.pub().info("Video Strem from IpCam is captured");
                            else
                                NeptusLog.pub().info("Video Strem from IpCam is not captured");
                        }
                        //TODO: Cap ip cam
                        else if(!raspiCam && ipCam && state) {
                            long startTime = System.currentTimeMillis();
                            capture.grab();
                            capture.read(mat);
                            xScale = (float) widhtConsole / mat.cols();
                            yScale = (float) heightConsole / mat.rows();
                            Imgproc.resize(mat, matResize, size);       
                            //Convert Mat to BufferedImage
                            temp=matToBufferedImage(matResize);
                            //Display image in JFrame
                            long stopTime = System.currentTimeMillis();
                            infoSizeStream = String.format("Size(%d x %d) | Scale(%.2f x %.2f) | FPS:%d |\t\t\t", mat.cols(), mat.rows(),(float)widhtConsole/mat.cols(),(float)heightConsole/mat.rows(),(int) 1000/(stopTime - startTime));
                            txtText.setText(infoSizeStream);
                            showImage(temp);
                            
                            if( captureFrame ) {
                                xPixel = xPixel - widhtConsole/2;
                                yPixel = -(yPixel - heightConsole/2);
                                String imageTag = String.format("%s/imageTag/(%d)_%s_X=%d_Y=%d.jpeg",logDir,cntTag,info,xPixel,yPixel);
                                outputfile = new File(imageTag);
                                try {
                                    ImageIO.write(temp, "jpeg", outputfile);
                                }
                                catch (IOException e) {
                                    e.printStackTrace();
                                }
                                captureFrame = false;
                                cntTag++;
                            }
                        }
                    }
                    else
                        inicImage();
                    
                    if (closingPanel)
                        break;
                }
            }
        };
        ret.setDaemon(true);
        return ret;
    }

    //!Thread to handle save image
    private Thread updaterThreadSave() {
        Thread si = new Thread("Save Image") {
            @Override
            public void run() {
                while(true){
                    if (raspiCam || ipCam) {
                        if(flagBuffImg == true){
                            //flagBuffImg = false;
                            long startTime = System.currentTimeMillis();
                            String imageJpeg = String.format("%s/imageSave/%d.jpeg",logDir,cnt);
                            outputfile = new File(imageJpeg);
                            try {
                                ImageIO.write(temp, "jpeg", outputfile);
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                            cnt++;
                            long stopTime = System.currentTimeMillis();
                            while((stopTime - startTime) < (1000/FPS)) {
                                stopTime = System.currentTimeMillis();
                            }
                        }
                        else{
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }     
                    }
                    else{
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (closingPanel)
                        break;
               }
           }
        };
        si.setDaemon(true);
        return si;
    }
    
    //!IMC handle
    @Subscribe
    public void consume(EstimatedState msg) {   
        //System.out.println("Source Name "+msg.getSourceName()+"ID "+getMainVehicleId());
        if(msg.getSourceName().equals(getMainVehicleId())){
            try {
                //! update the position of target
                //LAT and LON rad
                double latRad = msg.getLat();
                double lonRad = msg.getLon();
                //LAT and LON deg
                double latDeg = Math.toDegrees(latRad);//latRad*(180/Math.PI);
                double lonDeg = Math.toDegrees(lonRad);//lonRad*(180/Math.PI);

                LocationType locationType = new LocationType(latDeg,lonDeg);

                //Offset (m)
                double offsetN = msg.getX();
                double offsetE = msg.getY();
                
                //Height of Vehicle
                double heightRelative = msg.getHeight()-msg.getZ();//absolute altitude - zero of that location
                //System.out.println("heightRelative: h="+msg.getHeight()+" z="+msg.getZ());
                locationType.setOffsetNorth(offsetN);
                locationType.setOffsetEast(offsetE);
                locationType.setHeight(heightRelative);

                double camTiltDeg = 45.0f;//this value may be in configuration
                info = String.format("(IMC) LAT: %f # LON: %f # ALT: %.2f m", lat, lon, heightRelative);
                //System.out.println("lat: "+lat+" lon: "+lon+"heightV: "+heightV);
                LocationType tagLocationType = calcTagPosition(locationType.convertToAbsoluteLatLonDepth(), Math.toDegrees(msg.getPsi()), camTiltDeg);
                this.lat = tagLocationType.convertToAbsoluteLatLonDepth().getLatitudeDegs();
                this.lon = tagLocationType.convertToAbsoluteLatLonDepth().getLongitudeDegs();
                txtData.setText(info);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param locationType
     * @param orientationDegrees
     * @param camTiltDeg
     * @return
     */
    public LocationType calcTagPosition(LocationType locationType, double orientationDegrees, double camTiltDeg){
        double altitude = locationType.getHeight();
        double dist = Math.tan(Math.toRadians(camTiltDeg))*(Math.abs(altitude));// hypotenuse
        double offsetN = Math.cos(Math.toRadians(orientationDegrees))*dist;//oposite side
        double offsetE = Math.sin(Math.toRadians(orientationDegrees))*dist;// adjacent side

        LocationType tagLocationType = locationType.convertToAbsoluteLatLonDepth();
        tagLocationType.setOffsetNorth(offsetN);
        tagLocationType.setOffsetEast(offsetE);
        return tagLocationType.convertToAbsoluteLatLonDepth();
    }

    @Subscribe
    public void consume(Announce announce) {
        //System.out.println("Announce: "+announce.getSysName()+"  ID: "+announce.getSrc());
        //System.out.println("RECEIVED ANNOUNCE"+announce);
    }
    
    //!Fill cv::Mat image with zeros
    public void inicImage(){
        Scalar black = new Scalar(0);
        matResize.setTo(black);
        temp=matToBufferedImage(matResize);
        showImage(temp);
    }
    
    //!Received data Image
    public void receivedDataImage(){
        long startTime = System.currentTimeMillis();
        try {
            line = in.readLine();
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }
        if (line == null){
            //custom title, error icon
            JOptionPane.showMessageDialog(panelImage, I18n.text("Lost connection with vehicle"), I18n.text("Connection error"), JOptionPane.ERROR_MESSAGE);
            raspiCam = false;
            state = false;
            closeTcpCom();
        }
        else{        
            lengthImage = Integer.parseInt(line);
            //buffer for save data receive
            data = new byte[lengthImage];
            //Send 1 for server for sync data send
            out.println("1\0");
            //read data image (ZP)
            int read = 0;
            while (read < lengthImage) {
                int readBytes = 0;
                try {
                    readBytes = is.read(data, read, lengthImage-read);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                if (readBytes < 0) {
                    System.err.println("stream ended");
                    closeTcpCom();
                    return;
                }
                read += readBytes;
            }           
            //Receive data GPS over tcp DUNE
            try {
                duneGps = in.readLine();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }
            //Decompress data received 
            Inflater decompresser = new Inflater(false);
            decompresser.setInput(data,0,lengthImage);
            //Create an expandable byte array to hold the decompressed data
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            // Decompress the data
            byte[] buf = new byte[(widthImgRec * heightImgRec * 3)];
            while (!decompresser.finished()) 
            {
                try {
                    int count = decompresser.inflate(buf);                  
                    bos.write(buf, 0, count);
                } 
                catch (DataFormatException e) {
                    break;
                }
            }
            try {
                bos.close();
            } 
            catch (IOException e) {
            }
            // Get the decompressed data
            byte[] decompressedData = bos.toByteArray();
            
            //Transform byte data to cv::Mat (for display image)
            mat.put(0, 0, decompressedData);
            //Resize image to console size
            Imgproc.resize(mat, matResize, size);
                       
            //Convert Mat to BufferedImage
            temp=matToBufferedImage(matResize);    
            
            xScale = (float) widhtConsole / widthImgRec;
            yScale = (float) heightConsole / heightImgRec;
            long stopTime = System.currentTimeMillis();
            while((stopTime - startTime) < (1000/FPS))
                stopTime = System.currentTimeMillis();
            //Display image in JFrame
            info = String.format("Size(%d x %d) | Scale(%.2f x %.2f) | FPS:%d | Pak:%d (KiB:%d)", widthImgRec, heightImgRec,xScale,yScale,(int) 1000/(stopTime - startTime),lengthImage,lengthImage/1024);
            txtText.setText(info);
            txtDataTcp.setText(duneGps);
            showImage(temp);
        }
    }
    
    //!Close TCP COM
    public void closeTcpCom(){
        try {
            is.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        try {
            in.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        out.close();
        try {
            serverSocket.close();
        } catch (IOException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }
    }
    
    //!Create Socket service
    public void tcpConnection(){
        //Socket Config  
        try { 
            serverSocket = new ServerSocket(2424); 
        } 
        catch (IOException e) 
        { 
            System.err.println("Could not listen on port: "+ serverSocket); 
            System.exit(1); 
        } 
        Socket clientSocket = null; 
        NeptusLog.pub().info("Waiting for connection from RasPiCam...");
        try { 
            clientSocket = serverSocket.accept(); 
        } 
        catch (IOException e) 
        { 
            System.err.println("Accept failed."); 
            System.exit(1); 
        } 
        NeptusLog.pub().info("Connection successful from Server: "+clientSocket.getInetAddress()+":"+serverSocket.getLocalPort());
        NeptusLog.pub().info("Receiving data image from RasPiCam...");
        
        //Send data for sync 
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }

        //Buffer for data image
        try {
            is = clientSocket.getInputStream();
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }
        //Buffer for info of data image
        in = new BufferedReader( new InputStreamReader( is ));
    }
    
    /**  
     * Converts/writes a Mat into a BufferedImage.  
     * @param matrix Mat of type CV_8UC3 or CV_8UC1  
     * @return BufferedImage of type TYPE_3BYTE_BGR or TYPE_BYTE_GRAY  
     */  
    public static BufferedImage matToBufferedImage(Mat matrix) {
        int cols = matrix.cols();  
        int rows = matrix.rows();  
        int elemSize = (int)matrix.elemSize();  
        byte[] data = new byte[cols * rows * elemSize];  
        int type;  
        matrix.get(0, 0, data);  
        switch (matrix.channels()) {  
            case 1:  
                type = BufferedImage.TYPE_BYTE_GRAY;  
                break;  
            case 3:  
                type = BufferedImage.TYPE_3BYTE_BGR;  
                // bgr to rgb  
                byte b;  
                for(int i=0; i<data.length; i=i+3) {  
                    b = data[i];  
                    data[i] = data[i+2];  
                    data[i+2] = b;  
                }  
                break;  
        default:  
            return null;  
        }
        BufferedImage image2 = new BufferedImage(cols, rows, type);  
        image2.getRaster().setDataElements(0, 0, cols, rows, data);  
        return image2;
    }
}
