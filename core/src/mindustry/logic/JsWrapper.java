package mindustry.logic;

import arc.util.*;
import arc.util.Time;
import mindustry.gen.*;
import mindustry.logic.JsExecutor;
import mindustry.logic.LExecutor.*;
import mindustry.logic.LStatements.*;
import mindustry.game.Team;
import mindustry.world.blocks.logic.LogicBlock.*;
import mindustry.world.blocks.logic.LogicDisplay.*;
import mindustry.world.blocks.logic.MessageBlock.*;
import mindustry.world.meta.BlockFlag;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/* todo:
 * JsGeneric.color
 * Canvas.col
 * JS enum representation for
 *  - senseableType (?)
 *  - targetType
 *  - RadarSort
 *  - itemType
 *  - blockType
 *  - oreType
 *  - BlockFlag
 *  - Align
 */

public class JsWrapper {
    private JsExecutor executor;
    public CPU cpu;
    public JsExecutor.Console console;

    public class JsArray extends Scriptable{
        Boolean dummy;
    }
    public class JsObject extends Scriptable{
        Boolean dummy;
    }
    public class JsEnum extends Scriptable{
        Boolean dummy;
    }

    public JsWrapper(JsExecutor executor, JsExecutor.Console console, Scriptable scope) {
        this.executor = executor;
        this.console = console;
        cpu = new CPU(executor);

        ScriptableObject.putProperty(scope, "cpu", Context.javaToJS(cpu, scope));
        ScriptableObject.putProperty(scope, "console", Context.javaToJS(console, scope));

        JsEnum align = executor.context.newObject(executor.scope); 
        for(Align val : Align.values()){
            ScriptableObject.putConstProperty(align, val.toString(), val);
        }
        ScriptableObject.putProperty(scope, "Align", Context.javaToJS(align, scope));

    }

    public class CPU extends JsBuilding {
        private final LogicBuild logicBuild;
        public final JsCanvas canvas;
        public final JsMessage message;

        public CPU(JsExecutor executor) {
            super(executor.thisv);
            logicBuild = (LogicBuild) executor.thisv.objval;
            canvas = new JsCanvas(executor);
            message = new JsMessage(executor);
        }

        public JsObject links() {
            JsObject object = executor.context.newObject(executor.scope);
            for (LogicLink link : logicBuild.links) {
                if (link.lastBuild != null) {
                    JsBuilding generic = new JsBuilding(link.lastBuild);
                    Object genericWrapped = Context.javaToJS(generic, executor.scope);
                    ScriptableObject.putProperty(object, link.name, genericWrapped);
                }
            }
            return object;
        }

        public JsArray linkArray() {
            Object[] links = logicBuild.links
                    .select(link -> link.lastBuild != null)
                    .map(link -> Context.javaToJS(new JsBuilding(link.lastBuild), executor.scope))
                    .toArray(Object.class);
            return executor.context.newArray(executor.scope, links);
        }

        public JsBuilding link(String linkName) {
            LogicLink logicLink = logicBuild.links.find(l -> l.name == linkName);
            if (logicLink != null && logicLink.lastBuild != null) {
                return Context.javaToJS(new JsBuilding(logicLink.lastBuild), executor.scope);
            }
            return null;
        }

        public JsArray linkNames() {
            String linkNames[] = logicBuild.links.map(link -> (String) link.name).toArray(String.class);
            return executor.context.newArray(executor.scope, linkNames);

        }

        public JsUnit bind(String unitType) {
            LExecutor.UnitBindI unitBind = new LExecutor.UnitBindI(executor.builder.var(unitType));
            unitBind.run(executor);
            if (executor.unit != null) {
                return new JsUnit((Unit) executor.unit.objval);
            }
            return null;
        }

        public JsUnit bind(JsUnit unit) {
            LExecutor.UnitBindI unitBind = new LExecutor.UnitBindI(unit.target);
            unitBind.run(executor);
            if (executor.unit != null) {
                return new JsUnit((Unit) executor.unit.objval);
            }
            return null;
        }

        public void sleep(Double milliseconds) {
            executor.sleepUntil = Time.nanos() + Time.millisToNanos((long) milliseconds);
            executor.sendToYield();
        }

        public void yield() {
            executor.sendToYield();
        }

        public void print(String text){
            cpu.yield();
            p1.setobj(text);
            LExecutor.PrintI print = new LExecutor.PrintI(p1);
            print.run(executor);
        }

        public String format(Object object){
            return PrintI.toString(object);
        }

    }

    public class JsBuilding extends JsGeneric {

        public JsBuilding(Building b) {
            super(b);
        }

        public JsBuilding(JsBuilding original) {
            super(original);
        }

        public JsBuilding(LVar lvar) {
            super(lvar);
        }

    }

    public class JsGeneric {
        protected LVar ret = new LVar("ret");
        protected LVar p1 = new LVar("p1");
        protected LVar p2 = new LVar("p2");
        protected LVar p3 = new LVar("p3");
        protected LVar p4 = new LVar("p4");
        protected LVar p5 = new LVar("p5");
        protected LVar p6 = new LVar("p6");
        protected final LVar target;

        public JsGeneric(JsGeneric original) {
            target = original.target;
        }

        public JsGeneric(LVar lvar) {
            target = lvar;
        }

        public JsGeneric(String linkName) {
            LogicBuild logicBuild = (LogicBuild) executor.thisv.objval;
            LogicLink logicLink = logicBuild.links.find(l -> l.name == linkName);
            target = new LVar(logicLink.name);
            target.setobj(logicLink.lastBuild);
        }

        public JsGeneric(Building b) {
            target = new LVar("JsBuilding");
            target.setobj(b);
        }

        public JsGeneric(Unit u) {
            target = new LVar("JsUnit");
            target.setobj(u);
        }

        public Object sensor(String senseableType) {
            cpu.yield();
            LExecutor.SenseI sense = new LExecutor.SenseI(target, ret, executor.builder.var(senseableType));
            sense.run(executor);
            return ret.num();
        }

        public void shoot(Double x, Double y, Boolean shoot) {
            cpu.yield();
            p1.setnum(x);
            p2.setnum(y);
            p3.setbool(shoot);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.shoot, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void shootp(JsUnit unit, Boolean shoot) {
            cpu.yield();
            p1.setobj(unit.target);
            p2.setbool(shoot);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.shootp, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void color(String color) {
            cpu.yield();
            p1.setnum(0);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.color, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void setConfig(Object config) {
            cpu.yield();
            if(config instanceof Number){
                p1.setnum(config);
            }else{
                p1.setobj(config instanceof of JsGeneric ? config.target : config);
            }
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.config, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void setEnabled(Boolean value) {
            cpu.yield();
            p1.setbool(value);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.enabled, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public Double read(Double address) {
            cpu.yield();
            p1.setnum(address);
            LExecutor.ReadI read = new LExecutor.ReadI(target, p1, ret);
            read.run(executor);
            return ret.num();
        }

        public void write(Double address, Double value) {
            cpu.yield();
            p1.setnum(address);
            p2.setnum(value);
            LExecutor.WriteI write = new LExecutor.WriteI(target, p1, p2);
            write.run(executor);
        }

        public Object radar(String targetType1, String targetType2, String targetType3, Double order, String sort) {
            cpu.yield();
            p1.setnum(order);
            p3.setobj(executor.builder.var(targetType3));
            LExecutor.RadarI radar = new LExecutor.RadarI(RadarTarget.valueOf(targetType1),
                    RadarTarget.valueOf(targetType2),
                    RadarTarget.valueOf(targetType3), RadarSort.valueOf(sort), ret, p1, ret);
            radar.run(executor);
            if (ret.isobj) {
                if (ret.obj() instanceof Building b) {
                    return new JsBuilding(b);
                }
                if (ret.obj() instanceof Unit u) {
                    if (u.team == Team.derelict)
                        return new JsUnit(u);
                }
            }
            return null;
        }

        public void flush(){
            cpu.yield();
            if(target.building() instanceof LogicDisplayBuild){
                LExecutor.DrawFlushInst drawFlush = new LExecutor.DrawFlushInst(target);
                drawFlush.run(executor);
            }else if(target.building() instanceof MessageBuild){
                LExecutor.PrintFlushI printFlush = new LExecutor.PrintFlushI(target);
                printFlush.run(executor);    
            }
        }

        public String toString() {
            return PrintI.toString(target.objval);
        }
    }

    public class JsUnit extends JsGeneric {
        public JsUnit(LVar lvar) {
            super(lvar);
        }

        public JsUnit(Unit u) {
            super(u);
        }

        private void control(LUnitControl type) {
            cpu.yield();
            if (executor.unit.objval != target.objval)
                cpu.bind(this);
            LExecutor.UnitControlI unitControl = new LExecutor.UnitControlI(type, p1, p2, p3, p4, p5);
            unitControl.run(executor);
        }

        public void idle() {
            control(LUnitControl.idle);
        }

        public void move(Double x, Double y) {
            p1.setnum(x);
            p2.setnum(y);
            control(LUnitControl.move);
        }

        public void pathfind(Double x, Double y) {
            p1.setnum(x);
            p2.setnum(y);
            control(LUnitControl.pathfind);
        }

        public void approach(Double x, Double y) {
            p1.setnum(x);
            p2.setnum(y);
            control(LUnitControl.approach);
        }

        public void autoPathFind() {
            control(LUnitControl.autoPathfind);
        }

        public void stop() {
            control(LUnitControl.stop);
        }

        public Boolean within(Double x, Double y, Double radius) {
            p1.setnum(x);
            p2.setnum(y);
            p3.setnum(radius);
            control(LUnitControl.within);
            return p4.bool();
        }

        public void boost(Boolean value) {
            p1.setbool(value);
            control(LUnitControl.within);
        }

        public void target(Double x, Double y, Boolean shoot) {
            p1.setnum(x);
            p2.setnum(y);
            p3.setbool(shoot);
            control(LUnitControl.target);
        }

        public void targetp(JsUnit unit, Boolean shoot) {
            p1.setobj(unit.target.objval);
            p2.setbool(shoot);
            control(LUnitControl.targetp);
        }

        public void itemTake(JsBuilding fromBuilding, String itemType, Double amount) {
            p1.setobj(fromBuilding.target.objval);
            p2.setobj(executor.builder.var(itemType));
            p3.setnum(amount);
            control(LUnitControl.itemTake);
        }

        public void itemDrop(JsBuilding toBuilding, Double amount) {
            p1.setobj(toBuilding.target.objval);
            p2.setnum(amount);
            control(LUnitControl.itemDrop);
        }

        public void payloadTakeUnit() {
            p1.setbool(true);
            control(LUnitControl.payTake);
        }

        public void payloadTakeBlock() {
            p1.setbool(false);
            control(LUnitControl.payTake);
        }

        public void payloadDrop() {
            control(LUnitControl.payDrop);
        }

        public void payloadEnter() {
            control(LUnitControl.payEnter);
        }

        public void mine(Double x, Double y) {
            p1.setnum(x);
            p2.setnum(y);
            control(LUnitControl.mine);
        }

        public void build(Double x, Double y, String blockType, Double rotation, String config) {
            p1.setnum(x);
            p2.setnum(y);
            p3.setobj(executor.builder.var(blockType));
            p4.setnum(rotation);
            p5.setobj(executor.builder.var(config));
            control(LUnitControl.build);
        }

        public void flag(Double value) {
            p1.setnum(value);
            control(LUnitControl.flag);
        }

        public GetBlockResult getBlock(Double x, Double y) {
            p1.setnum(x);
            p2.setnum(y);
            control(LUnitControl.getBlock);

            // {Double type, Building building, Double floorType}
            JsBuilding building = null;
            if (p5.isobj && p5.objval instanceof Building b) {
                building = new JsBuilding(b);
            }
            return new GetBlockResult(p3.objval.toString(), building, p5.objval.toString());
        }

        private LocateResult locate(LLocate type) {
            return locate(type, BlockFlag.battery);
        }

        private LocateResult locate(LLocate type, BlockFlag blockFlag) {
            cpu.yield();
            if (executor.unit.objval != target.objval)
                cpu.bind(this);
            LExecutor.UnitLocateI unitLocate = new LExecutor.UnitLocateI(LLocate.building, blockFlag, p1, p1, p2, p3,
                    p4, ret);
            unitLocate.run(executor);
            if (p4.bool()) {
                if (ret.objval instanceof Building b) {
                    JsBuilding building = new JsBuilding(b);
                    return new LocateResult(p1.numval, p2.numval, building);
                } else {
                    return new LocateResult(p1.numval, p2.numval);
                }
            }
            return null;
        }

        public LocateResult locateBuilding(String group, Boolean enemy) {
            p1.setbool(enemy);
            return locate(LLocate.building, BlockFlag.valueOf(group));
        }

        public LocateResult locateOre(String oreType) {
            p1.setobj(executor.builder.var(oreType));
            return locate(LLocate.ore);
        }

        public Object locateSpawn() {
            return locate(LLocate.spawn);
        }

        public Object locateDamaged() {
            return locate(LLocate.damaged);
        }

        public void unbind() {
            if (executor.unit.objval != target.objval)
                cpu.bind(this);
            control(LUnitControl.unbind);
        }

        public class GetBlockResult {
            public String type, floor;
            public JsBuilding building;

            public GetBlockResult(String type, JsBuilding building, String floor) {
                this.building = building;
                this.type = type;
                this.floor = floor;
            }
        }

        public class LocateResult {
            public Double x, y;
            public JsBuilding building;

            public LocateResult(Double xval, Double yval) {
                x = xval;
                y = yval;
                building = null;
            }

            public LocateResult(Double xval, Double yval, JsBuilding building) {
                x = xval;
                y = yval;
                this.building = building;
            }
        }
    }

    public class JsMessage{
        private final LExecutor executor;
        protected LVar p1 = new LVar("p1");
        
        JsMessage(LExecutor executor){
            this.executor = executor;
        }

    }

    public class JsCanvas{
        protected LVar p1 = new LVar("p1");
        protected LVar p2 = new LVar("p2");
        protected LVar p3 = new LVar("p3");
        protected LVar p4 = new LVar("p4");
        protected LVar p5 = new LVar("p5");
        protected LVar p6 = new LVar("p6");

        private final LExecutor executor;

        JsCanvas(LExecutor executor){
            this.executor = executor;
        }

        public void draw(GraphicsType type){
            cpu.yield();
            DrawInst draw = new DrawInst((byte) type.ordinal(), p1, p2, p3, p4, p5, p6);
            draw.run(executor);
        }

        public void clear(Double r, Double g, Double b){
            p1.setnum(r);
            p2.setnum(g);
            p3.setnum(b);
            draw(GraphicsType.clear);
        }
        
        
        public void color(Double r, Double g, Double b, Double a){
            p1.setnum(r);
            p2.setnum(g);
            p3.setnum(b);
            p4.setnum(a);
            draw(GraphicsType.color);
        }
        
        
        /*public void col(String color){
            p1.setnum(r);
            p2.setnum(g);
            p3.setnum(b);
            p4.setnum(a);
            draw(GraphicsType.col);
        }*/
        
        
        public void stroke(Double width){
            p1.setnum(width);
            draw(GraphicsType.stroke);
        }
        
        
        public void line(Double x, Double y, Double x2, Double y2){
            p1.setnum(x);
            p2.setnum(y);
            p3.setnum(x2);
            p4.setnum(y2);
            draw(GraphicsType.line);
        }
        
        
        public void rect(Double x, Double y, Double x2, Double y2){
            p1.setnum(x);
            p2.setnum(y);
            p3.setnum(x2);
            p4.setnum(y2);
            draw(GraphicsType.rect);
        }
        
        
        public void lineRect(Double x, Double y, Double x2, Double y2){
            p1.setnum(x);
            p2.setnum(y);
            p3.setnum(x2);
            p4.setnum(y2);
            draw(GraphicsType.lineRect);
        }
        
        
        public void poly(Double x, Double y, Boolean sides, Double radius, Double rotation){
            p1.setnum(x);
            p2.setnum(y);
            p3.setbool(sides);
            p4.setnum(radius);
            p5.setnum(rotation);
            draw(GraphicsType.poly);        
        }
        
        
        public void linePoly(Double x, Double y, Boolean sides, Double radius, Double rotation){
            p1.setnum(x);
            p2.setnum(y);
            p3.setbool(sides);
            p4.setnum(radius);
            p5.setnum(rotation);
            draw(GraphicsType.linePoly);        
        }
        
        
        public void triangle(Double x, Double y, Double x2, Double y2, Double x3, Double y3){
            p1.setnum(x);
            p2.setnum(y);
            p3.setnum(x2);
            p4.setnum(y2);
            p5.setnum(x3);
            p6.setnum(y3);
            draw(GraphicsType.triangle);                
        }
        
        
        public void image(Double x, Double y, String image, Double size, Double rotation){
            p1.setnum(x);
            p2.setnum(y);            
            p3.setobj(((JsExecutor) executor).builder.var(image).objval);
            p4.setnum(size);
            p5.setnum(rotation);
            draw(GraphicsType.image);                
        }
        
        
        public void print(String text, Double x, Double y, String align){
            cpu.print(text);
            p1.setnum(x);
            p2.setnum(y);            
            p3.setnum(DrawStatement.nameToAlign.get(align, Align.bottomLeft));
            draw(GraphicsType.print);                  
        }
        
        
        public void translate(Double x, Double y){
            p1.setnum(x);
            p2.setnum(y);            
            draw(GraphicsType.print);           
        }
        
        
        public void scale(Double x, Double y){
            p1.setnum(x);
            p2.setnum(y);            
            draw(GraphicsType.scale);    
        }
    
    
        public void rotate(Double x, Double y){
            p1.setnum(x);
            p2.setnum(y);            
            draw(GraphicsType.rotate);           
        }
    }
}