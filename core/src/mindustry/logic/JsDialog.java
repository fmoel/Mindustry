package mindustry.logic;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.scene.actions.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.LExecutor.*;
import mindustry.logic.LStatements.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.blocks.logic.*;

import static mindustry.Vars.*;
import static mindustry.logic.LCanvas.*;

public class JsDialog extends BaseDialog {
    @Nullable private JsExecutor executor;
    Cons<String> consumer = s -> {};
    private TextArea codeEditor = new TextArea("");
    private TextArea consoleOutput = new TextArea("console.log():");
    private Table controlButtons = new Table();
    private boolean isRunning = false;
    private GlobalVarsDialog globalsDialog = new GlobalVarsDialog();

    public JsDialog() {
        super("JavaScript Logic Editor");

        clearChildren();

        isRunning = false;
        shouldPause = true;

        addCloseListener();

        // Setup listeners
        shown(this::setup);
        hidden(() -> consumer.get(codeEditor.getText()));
        onResize(this::setup);
    }

    private void setup() {
        // Code editor setup
        codeEditor.setPrefRows(15);
        codeEditor.setMessageText("Enter your JavaScript code here...");
        
        // Console output setup
        consoleOutput.setPrefRows(5);

        // Control buttons setup
        controlButtons.defaults().size(120f, 50f);
        setupControlButtons();

        // Layout setup
        clearChildren();
        add(codeEditor).grow().pad(10f);
        row();
        add(controlButtons).growX().pad(5f);
        row();
        add(consoleOutput).growX().pad(5f);
    }

    public static Color typeColor(LVar s, Color color){
      return color.set(
          !s.isobj ? Pal.place :
          s.objval == null ? Color.darkGray :
          s.objval instanceof String ? Pal.ammo :
          s.objval instanceof Content ? Pal.logicOperations :
          s.objval instanceof Building ? Pal.logicBlocks :
          s.objval instanceof Unit ? Pal.logicUnits :
          s.objval instanceof Team ? Pal.logicUnits :
          s.objval instanceof Enum<?> ? Pal.logicIo :
          Color.white
      );
  }

  public static String typeName(LVar s){
      return
          !s.isobj ? "number" :
          s.objval == null ? "null" :
          s.objval instanceof String ? "string" :
          s.objval instanceof Content ? "content" :
          s.objval instanceof Building ? "building" :
          s.objval instanceof Team ? "team" :
          s.objval instanceof Unit ? "unit" :
          s.objval instanceof Enum<?> ? "enum" :
          "unknown";
  }    

    private void setupControlButtons() {
      controlButtons.clearChildren();

      controlButtons.button("@back", Icon.left, this::hide).name("back");
        controlButtons.button("@step", Icon.play, () -> {
            executor.runOnce();
            consoleOutput.setText("current line: " + executor.getCurrentLineNumber()); 
        }).disabled(t -> net.active());

        if(isRunning){
          controlButtons.button("@pause", Icon.pause, () -> {
              isRunning = false;
              state.set(State.paused);
          }).disabled(t -> net.active());
        }else{
            controlButtons.button("@play", Icon.play, () -> {
                state.set(State.playing);
                isRunning = true;
            }).disabled(t -> net.active());
        }

        controlButtons.button("@reset", Icon.cancel, () -> {
            executor.load(codeEditor.getText());
            isRunning = false;
            state.set(State.paused);
        }).disabled(t -> net.active());

        controlButtons.button("@variables", Icon.menu, () -> {
          BaseDialog dialog = new BaseDialog("@variables");
          dialog.hidden(() -> {
              if(!wasPaused && !net.active()){
                  state.set(State.paused);
              }
          });

          dialog.shown(() -> {
              if(!wasPaused && !net.active()){
                  state.set(State.playing);
              }
          });

          dialog.cont.pane(p -> {
              p.margin(10f).marginRight(16f);
              p.table(Tex.button, t -> {
                  t.defaults().fillX().height(45f);
                  for(var s : executor.vars){
                      if(s.constant) continue;

                      Color varColor = Pal.gray;
                      float stub = 8f, mul = 0.5f, pad = 4;

                      t.add(new Image(Tex.whiteui, varColor.cpy().mul(mul))).width(stub);
                      t.stack(new Image(Tex.whiteui, varColor), new Label(" " + s.name + " ", Styles.outlineLabel){{
                          setColor(Pal.accent);
                      }}).padRight(pad);

                      t.add(new Image(Tex.whiteui, Pal.gray.cpy().mul(mul))).width(stub);
                      t.table(Tex.pane, out -> {
                          float period = 15f;
                          float[] counter = {-1f};
                          Label label = out.add("").style(Styles.outlineLabel).padLeft(4).padRight(4).width(140f).wrap().get();
                          label.update(() -> {
                              if(counter[0] < 0 || (counter[0] += Time.delta) >= period){
                                  String text = s.isobj ? PrintI.toString(s.objval) : Math.abs(s.numval - (long)s.numval) < 0.00001 ? (long)s.numval + "" : s.numval + "";
                                  if(!label.textEquals(text)){
                                      label.setText(text);
                                      if(counter[0] >= 0f){
                                          label.actions(Actions.color(Pal.accent), Actions.color(Color.white, 0.2f));
                                      }
                                  }
                                  counter[0] = 0f;
                              }
                          });
                          label.act(1f);
                      }).padRight(pad);

                      t.add(new Image(Tex.whiteui, typeColor(s, new Color()).mul(mul))).update(i -> i.setColor(typeColor(s, i.color).mul(mul))).width(stub);

                      t.stack(new Image(Tex.whiteui, typeColor(s, new Color())){{
                          update(() -> setColor(typeColor(s, color)));
                      }}, new Label(() -> " " + typeName(s) + " "){{
                          setStyle(Styles.outlineLabel);
                      }});

                      t.row();

                      t.add().growX().colspan(6).height(4).row();
                  }
              });
          });

          dialog.addCloseButton();
          dialog.buttons.button("@logic.globals", Icon.list, () -> globalsDialog.show()).size(210f, 64f);

          dialog.show();
      }).name("variables").disabled(b -> executor == null || executor.vars.length == 0);
    }
    
    
    public void show(String code, JsExecutor executor, boolean privileged, Cons<String> modified) {
        this.executor = executor;
        codeEditor.setText(executor.code);
        executor.setConsoleListener(log -> {
            if(consoleOutput != null)
                consoleOutput.setText(log);  
        });

        this.consumer = result -> {
            if(!result.equals(code)){
                modified.get(result);
            }
        };

        show();
    }
}
